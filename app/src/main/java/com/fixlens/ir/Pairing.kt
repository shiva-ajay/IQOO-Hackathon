package com.fixlens.ir

/**
 * Finds which of a brand's remotes works, the way a universal remote pairs (docs/ir-remote-plan.md §5), but
 * trying each **distinct** code once: models whose first test code is identical form one group, so twenty LG
 * TV remotes sharing four mute codes take at most four tries.
 *
 * Test 1 (a safe button: mute, menu, AC "on at 24°") picks the group; test 2 (volume up, back, AC "25°") picks
 * the model inside it and guards against a code shared by unrelated remotes. If test 1 worked somewhere but no
 * test 2 ever did, that model is still offered at the end, marked unverified. Pure logic, unit-tested.
 */
class Pairing(candidates: List<Candidate>) {

    /** [model]: index into the brand's model list. [test1]/[test2]: content keys of its test codes, null if missing. */
    data class Candidate(val model: Int, val test1: String?, val test2: String?)

    sealed interface Step {
        /** Send test [stage] (1 or 2) of [model]; [attempt] is the 1-based code number out of [attempts]. */
        data class Test(val stage: Int, val model: Int, val attempt: Int, val attempts: Int) : Step
        data class Paired(val model: Int, val verified: Boolean) : Step
        data object NoMatch : Step
    }

    private class Group(val members: List<Candidate>) {
        /** Members split by their second test code, in order; members without one can't be told apart. */
        val second: List<List<Candidate>> = members.filter { it.test2 != null }.groupBy { it.test2 }.values.toList()
    }

    private val groups: List<Group> =
        candidates.filter { it.test1 != null }.groupBy { it.test1 }.values.map(::Group)

    private var group = 0
    private var stage = 1
    private var sub = 0
    private var partial: Int? = null
    private var result: Step? = if (groups.isEmpty()) Step.NoMatch else null

    val tries: Int get() = groups.size

    /** Where the search is, to step back to (an "it responded" tapped just after the next code was chosen). */
    class Mark internal constructor(val group: Int, val stage: Int, val sub: Int, val partial: Int?, val result: Step?)

    fun mark(): Mark = Mark(group, stage, sub, partial, result)

    fun restore(m: Mark) {
        group = m.group
        stage = m.stage
        sub = m.sub
        partial = m.partial
        result = m.result
    }

    fun current(): Step = result ?: when (stage) {
        1 -> Step.Test(1, groups[group].members.first().model, group + 1, groups.size)
        else -> Step.Test(2, groups[group].second[sub].first().model, group + 1, groups.size)
    }

    /** The device reacted to the code just sent. */
    fun responded(): Step {
        if (result != null) return result!!
        val g = groups[group]
        if (stage == 1) {
            if (g.second.isEmpty()) return finish(Step.Paired(g.members.first().model, verified = false))
            stage = 2
            sub = 0
        } else {
            return finish(Step.Paired(g.second[sub].first().model, verified = true))
        }
        return current()
    }

    /** Nothing happened. */
    fun noResponse(): Step {
        if (result != null) return result!!
        if (stage == 2) {
            sub++
            if (sub < groups[group].second.size) return current()
            // Test 1 worked here but no second code did: keep it as the fallback, look for a better match.
            if (partial == null) partial = groups[group].members.first().model
        }
        stage = 1
        sub = 0
        group++
        if (group >= groups.size) return finish(partial?.let { Step.Paired(it, verified = false) } ?: Step.NoMatch)
        return current()
    }

    private fun finish(step: Step): Step {
        result = step
        return step
    }
}

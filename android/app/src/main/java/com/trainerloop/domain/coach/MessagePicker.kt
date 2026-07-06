package com.trainerloop.domain.coach

import kotlin.random.Random

/**
 * Per-rule template selection (§8.5): random pick with no-repeat-last-2 and
 * `{{placeholder}}` substitution. Seeded so replays stay deterministic (§10).
 */
class MessagePicker(
  private val templates: Map<String, List<String>>,
  seed: Long = 0
) {
  private val random = Random(seed)
  private val recent = mutableMapOf<String, MutableList<Int>>()

  fun message(ruleId: String, fallback: String, data: Map<String, Any> = emptyMap()): String {
    val list = templates[ruleId].orEmpty()
    val template = if (list.isEmpty()) fallback else pick(ruleId, list)
    return data.entries.fold(template) { acc, (k, v) -> acc.replace("{{$k}}", v.toString()) }
  }

  private fun pick(ruleId: String, list: List<String>): String {
    val last = recent.getOrPut(ruleId) { mutableListOf() }
    val candidates = list.indices.filter { it !in last }.ifEmpty { list.indices.toList() }
    val idx = candidates[random.nextInt(candidates.size)]
    last.add(idx)
    while (last.size > minOf(2, list.size - 1)) last.removeAt(0)
    return list[idx]
  }
}

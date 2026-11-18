import { onBeforeUnmount, ref } from 'vue'

export function useDelayedLoading(delay = 120) {
  const visible = ref(false)
  let timer: ReturnType<typeof setTimeout> | undefined
  let generation = 0

  function start(): number {
    generation += 1
    const current = generation
    if (timer) clearTimeout(timer)
    visible.value = false
    timer = setTimeout(() => {
      if (current === generation) visible.value = true
    }, delay)
    return current
  }

  function isCurrent(request: number): boolean {
    return request === generation
  }

  function finish(request: number): void {
    if (request !== generation) return
    if (timer) clearTimeout(timer)
    timer = undefined
    visible.value = false
  }

  function cancel(): void {
    generation += 1
    if (timer) clearTimeout(timer)
    timer = undefined
    visible.value = false
  }

  onBeforeUnmount(cancel)
  return { visible, start, isCurrent, finish, cancel }
}

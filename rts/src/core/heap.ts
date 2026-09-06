/**
 * Binary min-heap.
 *
 * A* previously scanned its open list linearly to find the lowest-cost node, which is
 * O(n) per pop and made long searches quadratic -- the single largest cost in the
 * whole simulation. This makes it O(log n).
 */
export class MinHeap<T> {
  private readonly items: T[] = [];
  private readonly score: (item: T) => number;

  constructor(score: (item: T) => number) {
    this.score = score;
  }

  get size(): number {
    return this.items.length;
  }

  push(item: T): void {
    this.items.push(item);
    this.bubbleUp(this.items.length - 1);
  }

  pop(): T | undefined {
    const items = this.items;
    if (items.length === 0) return undefined;
    const top = items[0];
    const last = items.pop() as T;
    if (items.length > 0) {
      items[0] = last;
      this.sinkDown(0);
    }
    return top;
  }

  private bubbleUp(index: number): void {
    const items = this.items;
    const item = items[index];
    const value = this.score(item);
    let i = index;
    while (i > 0) {
      const parent = (i - 1) >> 1;
      if (this.score(items[parent]) <= value) break;
      items[i] = items[parent];
      i = parent;
    }
    items[i] = item;
  }

  private sinkDown(index: number): void {
    const items = this.items;
    const length = items.length;
    const item = items[index];
    const value = this.score(item);
    let i = index;
    for (;;) {
      const left = i * 2 + 1;
      const right = left + 1;
      let best = i;
      let bestScore = value;
      if (left < length) {
        const s = this.score(items[left]);
        if (s < bestScore) {
          best = left;
          bestScore = s;
        }
      }
      if (right < length) {
        const s = this.score(items[right]);
        if (s < bestScore) {
          best = right;
          bestScore = s;
        }
      }
      if (best === i) break;
      items[i] = items[best];
      i = best;
    }
    items[i] = item;
  }
}

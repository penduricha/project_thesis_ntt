export const norm = t => (t || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/gi, 'd').toLowerCase()

/* So sánh theo từ (LCS) – trả về token để hiển thị <del>/<ins> mà không cần v-html */
export function diffWords(oldStr, newStr) {
  const a = oldStr.split(/\s+/), b = newStr.split(/\s+/), m = a.length, n = b.length
  const dp = Array.from({ length: m + 1 }, () => Array(n + 1).fill(0))
  for (let i = m - 1; i >= 0; i--) for (let j = n - 1; j >= 0; j--) dp[i][j] = a[i] === b[j] ? 1 + dp[i + 1][j + 1] : Math.max(dp[i + 1][j], dp[i][j + 1])
  let i = 0, j = 0; const o = [], nw = []
  while (i < m && j < n) {
    if (a[i] === b[j]) { o.push({ t: a[i], k: 'same' }); nw.push({ t: b[j], k: 'same' }); i++; j++ }
    else if (dp[i + 1][j] >= dp[i][j + 1]) o.push({ t: a[i++], k: 'del' })
    else nw.push({ t: b[j++], k: 'ins' })
  }
  while (i < m) o.push({ t: a[i++], k: 'del' })
  while (j < n) nw.push({ t: b[j++], k: 'ins' })
  return { oldTok: o, newTok: nw }
}

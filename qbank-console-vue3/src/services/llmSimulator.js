import { LV } from '../data/mock.js'
/* Mô phỏng Qwen2.5 (giữ nguyên logic bản gốc) – sau này thay bằng gọi Server 2 */
const TYPO={'ngử':'ngữ','dử liệu':'dữ liệu'};
export function llm(q){
  const is=[];let tx=q.text;
  for(const k in TYPO)if(tx.includes(k)){is.push({k:'Chính tả',d:`“${k}” nên viết chuẩn chính tả là “${TYPO[k]}”`});tx=tx.split(k).join(TYPO[k])}
  if(!/[?:.]$/.test(tx.trim())){is.push({k:'Ngữ pháp',d:'Câu dẫn thiếu dấu kết thúc câu (?)'});tx=tx.trim()+'?'}
  if(new Set(q.o.map(x=>x.trim().toLowerCase())).size<4)is.push({k:'Phương án nhiễu',d:'Có hai phương án trùng nội dung nên không có giá trị phân loại thí sinh'});
  const L=q.o.map(x=>x.length);
  if(L[q.a]>1.6*Math.max(...L.filter((_,i)=>i!==q.a)))is.push({k:'Logic',d:'Đáp án đúng dài và chi tiết hơn hẳn các phương án nhiễu nên dễ bị đoán trúng'});
  const high=is.some(x=>x.k==='Logic'||x.k==='Phương án nhiễu');
  return{is,tx,diff:LV[Math.min(2,Math.floor(q.text.length/40))],sev:high?'Cao':'Trung bình'}
}

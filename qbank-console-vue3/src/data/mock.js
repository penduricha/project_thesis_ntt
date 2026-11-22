/* DỮ LIỆU GIẢ TOÀN BỘ – bản demo giao diện. Mọi tên, mã admin và mốc thời gian trong file này đều không phải dữ liệu thật.
   Khi mở rộng lên feature thật, thay file này bằng dữ liệu lấy từ API Spring Boot/MariaDB. */
/* Phân cấp Môn học (Course) -> Chương / Chủ đề con (Topics) chuẩn Moodle */
export const COURSES=[
  {
    id:'INT3306',
    name:'Lập trình Java',
    dept:'Khoa Công nghệ thông tin',
    topics:['Chương 1: Cú pháp & OOP cơ bản','Chương 2: Ngoại lệ & Collections','Chương 3: Spring Boot & REST API']
  },
  {
    id:'INT3202',
    name:'Cơ sở dữ liệu',
    dept:'Khoa Công nghệ thông tin',
    topics:['Chương 1: Mô hình quan hệ & Khóa','Chương 2: Ngôn ngữ truy vấn SQL','Chương 3: NoSQL & Lưu trữ phân tán']
  },
  {
    id:'INT3304',
    name:'Mạng máy tính',
    dept:'Khoa Công nghệ thông tin',
    topics:['Chương 1: Kiến trúc phân tầng OSI/TCP-IP','Chương 2: Giao thức ứng dụng HTTP/REST','Chương 3: Bảo mật mạng & Xác thực']
  },
  {
    id:'INT3401',
    name:'Trí tuệ nhân tạo',
    dept:'Khoa Công nghệ thông tin',
    topics:['Chương 1: Tìm kiếm & Biểu diễn tri thức','Chương 2: Học máy & Mạng nơ-ron','Chương 3: Mô hình ngôn ngữ lớn (LLM)']
  },
  {
    id:'BAS1150',
    name:'Triết học Mác – Lênin',
    dept:'Khoa Lý luận chính trị',
    topics:['Chương 1: Chủ nghĩa duy vật biện chứng','Chương 2: Phép biện chứng duy vật','Chương 3: Chủ nghĩa duy vật lịch sử']
  }
];

/* Dữ liệu mẫu bổ sung: nhiều môn/chương để thử ô gõ-tìm (datalist) */
[['INT3105','Cấu trúc dữ liệu và giải thuật','Độ phức tạp thuật toán|Danh sách, ngăn xếp, hàng đợi|Cây nhị phân & BST|Đồ thị|Sắp xếp & tìm kiếm'],
['INT3507','Hệ điều hành','Tiến trình & luồng|Lập lịch CPU|Đồng bộ hóa|Quản lý bộ nhớ|Hệ thống tệp'],
['INT3310','Công nghệ phần mềm','Quy trình phát triển|Phân tích yêu cầu|Thiết kế UML|Kiểm thử phần mềm'],
['INT3409','Học máy','Hồi quy & phân loại|Cây quyết định|Mạng nơ-ron|Đánh giá mô hình'],
['INT3412','Xử lý ngôn ngữ tự nhiên','Tiền xử lý văn bản|Biểu diễn từ (embedding)|Transformer|Prompt Engineering'],
['INT3601','An toàn thông tin','Mật mã học|Xác thực & TOTP|Bảo mật ứng dụng web|Quản lý rủi ro'],
['INT3215','Phát triển ứng dụng web','HTML/CSS/JS|Vue.js & SPA|REST API|Triển khai Docker'],
['INT3216','Phát triển ứng dụng di động','Dart & Flutter|Quản lý trạng thái|Gọi API với Dio|Kiểm thử trên thiết bị'],
['BAS1201','Toán rời rạc','Logic mệnh đề|Tập hợp & quan hệ|Tổ hợp|Lý thuyết đồ thị'],
['BAS1105','Giải tích 1','Giới hạn & liên tục|Đạo hàm|Tích phân|Chuỗi số'],
['ENG1101','Tiếng Anh chuyên ngành CNTT','Từ vựng chuyên ngành|Đọc tài liệu kỹ thuật|Viết email kỹ thuật'],
['BAS1160','Tư tưởng Hồ Chí Minh','Cơ sở hình thành|Độc lập dân tộc|Đại đoàn kết|Xây dựng Nhà nước']
].forEach(([id,name,t])=>COURSES.push({id,name,dept:'',topics:t.split('|').map((x,i)=>'Chương '+(i+1)+': '+x)}));
['Lập trình hướng đối tượng, Spring Boot và REST API','Mô hình quan hệ, SQL, chuẩn hóa và chỉ mục','Giao thức, HTTP/REST và kiến trúc mạng','Tìm kiếm, học máy và mô hình ngôn ngữ','Chủ nghĩa duy vật biện chứng và lịch sử'].forEach((d,i)=>COURSES[i].dept=d);
export const LV=['Dễ','Trung bình','Khó'];
export const LV_DB=[1,2,3]; /* JS -> ntt_questions.difficulty (tinyint, CHECK 1..3 sau chuẩn hóa v5): Dễ=1, Trung bình=2, Khó=3 */
export const ENUM_DB={st:{draft:'not_checked',ok:'clean',flag:'has_issues',stale:'stale'},sev:{'Cao':'high','Trung bình':'medium'}}; /* JS -> enum SQL */
COURSES.forEach((c,i)=>{c.subjectId=101+i;c.topicIds=c.topics.map((_,k)=>1001+i*10+k);c.active=true}); /* subject_id / topic_id (BIGINT) mẫu */
export const ICO={
  courses:'<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>',
  llm:'<rect x="6" y="6" width="12" height="12" rx="2"/><path d="M9 2v4M15 2v4M9 18v4M15 18v4M2 9h4M2 15h4M18 9h4M18 15h4"/>',
  dash:'<rect x="3" y="3" width="7" height="9" rx="1"/><rect x="14" y="3" width="7" height="5" rx="1"/><rect x="14" y="12" width="7" height="9" rx="1"/><rect x="3" y="16" width="7" height="5" rx="1"/>',
  bank:'<path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"/>',
  search:'<circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3"/>',
  review:'<path d="M20 6 9 17l-5-5"/>',
  tasks:'<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  moodle:'<path d="M12 3v12m0 0-4-4m4 4 4-4M4 17v3h16v-3"/>',
  admin:'<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>'
};
export const NAV=[
  ['dash','Tổng quan','Tổng quan'],
  ['bank','Câu hỏi','Tất cả câu hỏi'],
  ['courses','Câu hỏi','Môn học'],
  ['search','Câu hỏi','Tìm kiếm thông minh'],
  ['review','Kiểm định AI','Xem xét đề xuất AI'],
  ['tasks','Kiểm định AI','Tác vụ AI'],
  ['moodle','Tích hợp','Moodle XML'],
  ['admin','Quản trị','Quản trị hệ thống'],
  ['llm','Quản trị','Cài đặt LLM']
];
export const EXTRA=[['account','','Thông tin tài khoản'],['settings','','Cài đặt'],['course','','Câu hỏi theo môn học']];
export const SUB={
  courses:'Chọn một môn học để quản lý các chương/chủ đề con và ngân hàng câu hỏi bên trong',
  course:'Lọc theo chương/chủ đề con, thêm, sửa, xóa và kiểm định câu hỏi thuộc môn học này',
  llm:'Quản lý tối đa 5 node Colab (Cloudflare Quick Tunnel): dán kết quả, kiểm tra, lưu lên Server 2.',
  dash:'Tình hình ngân hàng câu hỏi theo học phần và các dịch vụ suy luận',
  bank:'Thêm, sửa, xóa, lọc và chọn câu hỏi để kiểm định hoặc xuất Moodle',
  search:'Tìm câu hỏi bằng ngôn ngữ tự nhiên qua phân tích cấu trúc 3 bước',
  review:'So sánh nội dung gốc và duyệt đề xuất của AI trước khi cập nhật chính thức',
  tasks:'Theo dõi tiến độ và kết quả của các yêu cầu kiểm định',
  moodle:'Xuất và nhập câu hỏi chuẩn Moodle XML (.xml) gắn theo môn học và chương',
  admin:'Tra cứu tài khoản, quản lý vai trò và quyền truy cập.',
  account:'Thông tin cá nhân và quá trình đào tạo',
  settings:'Bảo mật hai yếu tố TOTP và tùy chọn hiển thị'
};

export const stB={draft:['Chưa kiểm định','n'],ok:['Đã kiểm định','ok'],flag:['Có đề xuất AI','wa'],stale:['Cần kiểm định lại','wa']}; /* validation_status: not_checked, clean, has_issues, stale */
export const TS={pending:['Đã tiếp nhận','n'],running:['Đang xử lý','wa'],succeeded:['Hoàn tất','ok'],failed:['Thất bại','er']};
export const IC={'Chính tả':'wa','Ngữ pháp':'wa','Logic':'er','Phương án nhiễu':'er'};

export const QUESTIONS=[
    {id:1,text:'Spring Boot được phát triển dựa trên ngôn ngử lập trình nào?',o:['Java','Python','Go','Rust'],a:0,c:0,tp:2,l:0,st:'draft',updatedAt:'18/11 08:15',by:'Demo Admin'},
    {id:2,text:'Khóa chính trong cơ sở dử liệu quan hệ dùng để làm gì?',o:['Xác định duy nhất mỗi bản ghi','Mã hóa dữ liệu','Sao lưu bảng','Tăng dung lượng'],a:0,c:1,tp:0,l:0,st:'draft',updatedAt:'18/11 08:20',by:'Giảng viên A (demo)'},
    {id:3,text:'Phương thức HTTP nào thường dùng để tạo mới tài nguyên trong RESTful API?',o:['GET','POST','DELETE','HEAD'],a:1,c:2,tp:1,l:0,st:'ok',updatedAt:'17/11 16:40',by:'Demo Admin'},
    {id:4,text:'Redis thuộc loại hệ lưu trữ nào',o:['Khóa–giá trị trong bộ nhớ','Quan hệ','Đồ thị','Cột rộng'],a:0,c:1,tp:2,l:1,st:'draft',updatedAt:'17/11 15:10',by:'Giảng viên A (demo)'},
    {id:5,text:'Cơ chế nào giúp Transformer xác định mức liên quan giữa các token?',o:['Self-Attention','Pooling','Dropout','Padding'],a:0,c:3,tp:2,l:1,st:'draft',updatedAt:'17/11 14:05',by:'Demo Admin'},
    {id:6,text:'Thuật toán TOTP mặc định tạo mã mới sau bao lâu?',o:['30 giây','5 giây','10 phút','1 giờ'],a:0,c:2,tp:2,l:1,st:'ok',updatedAt:'16/11 11:30',by:'Demo Admin'},
    {id:7,text:'Lượng tử hóa Q4_K_M ảnh hưởng thế nào đến mô hình ngôn ngữ lớn?',o:['Giảm dung lượng bộ nhớ nhưng có thể làm giảm chất lượng đầu ra','Luôn tăng độ chính xác','Không thay đổi gì','Tăng số tham số'],a:0,c:3,tp:2,l:2,st:'draft',updatedAt:'16/11 10:12',by:'Giảng viên B (demo)'},
    {id:8,text:'Lệnh SQL nào dùng để xóa các bản ghi thỏa điều kiện?',o:['DELETE','DELETE','DROP','SELECT'],a:0,c:1,tp:1,l:0,st:'draft',updatedAt:'15/11 17:00',by:'Giảng viên A (demo)'},
    {id:9,text:'Vấn đề cơ bản của triết học là mối quan hệ giữa hai phạm trù nào?',o:['Vật chất và ý thức','Chân lý và sai lầm','Thiện và ác','Lý thuyết và thực tiễn'],a:0,c:4,tp:0,l:1,st:'draft',updatedAt:'15/11 09:45',by:'Giảng viên B (demo)'},
    {id:10,text:'Theo chủ nghĩa duy vật, yếu tố nào quyết định ý thức?',o:['Vật chất','Thượng đế','Ý niệm tuyệt đối','Cảm giác thuần túy'],a:0,c:4,tp:0,l:1,st:'ok',updatedAt:'14/11 16:20',by:'Demo Admin'},
    {id:11,text:'Phép biện chứng duy vật nghiên cứu điều gì?',o:['Những quy luật chung nhất của sự vận động và phát triển','Cấu tạo của nguyên tử','Cách viết văn bản hành chính','Lịch sử các triều đại'],a:0,c:4,tp:1,l:2,st:'draft',updatedAt:'14/11 14:10',by:'Giảng viên B (demo)'}
];

export const MY_ADMIN_ID='DEMO-0001';
export const USERS=[
    {adminId:MY_ADMIN_ID,name:'Demo Admin',role:'Quản trị viên',totp:'ENABLED',status:'active',passwordChangedAt:'12/11 09:00'},
    {adminId:'DEMO-0002',name:'Admin hệ thống (demo)',role:'Quản trị viên',totp:'ENABLED',status:'active',passwordChangedAt:'10/11 14:30'},
    {adminId:'DEMO-0003',name:'Giảng viên A (demo)',role:'Giảng viên',totp:'ENABLED',status:'active',passwordChangedAt:'08/11 08:15'},
    {adminId:'DEMO-0004',name:'Giảng viên B (demo)',role:'Giảng viên',totp:'NOT_ENABLED',status:'active',passwordChangedAt:'05/11 16:40'},
    {adminId:'DEMO-0005',name:'Giảng viên C (demo)',role:'Giảng viên',totp:'PENDING',status:'pending',passwordChangedAt:null}
];

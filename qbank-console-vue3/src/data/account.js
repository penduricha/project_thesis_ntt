import { MY_ADMIN_ID } from './mock.js'
/* Dữ liệu mẫu khớp ntt_admins + ntt_courses + ntt_totp_credentials (trích từ UI_Accept_v10.html) */
export const ME={adminId:MY_ADMIN_ID,name:'Từ Quang Nhật',gender:true,dob:'2003-12-25',address:'467/134/20 QL1A, An Phú Đông, Tphcm.',idCard:'060203000027',
  course:{faculty:'CNTT',industry:'AI (MLOPS)',major:'AI/ML',trainingSystem:'Master',typeOfTraining:'Research',branch:'An Phu Dong ward, Ho Chi Minh city',yearOpening:2025}};
export const TRAIN_SYS={Master:'Thạc sĩ',Bachelor:'Đại học',Doctor:'Tiến sĩ',PhD:'Tiến sĩ'};export const TRAIN_TYPE={Research:'Nghiên cứu',Application:'Ứng dụng'};
export const ACI={user:'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0z',book:'M2 4h6a4 4 0 0 1 4 4v13a3 3 0 0 0-3-3H2zM22 4h-6a4 4 0 0 0-4 4v13a3 3 0 0 1 3-3h7z',pin:'M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0zM15 10a3 3 0 1 1-6 0 3 3 0 0 1 6 0z',card:'M3 5h18a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zM6 10h5M6 14h3M15 12a1.5 1.5 0 1 0 3 0 1.5 1.5 0 0 0-3 0z',eye:'M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12zM15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z',eyeOff:'M17.94 17.94A10.07 10.07 0 0 1 12 19c-6.5 0-10-7-10-7a18.5 18.5 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c6.5 0 10 7 10 7a18.5 18.5 0 0 1-2.16 3.19M14.12 14.12a3 3 0 1 1-4.24-4.24M1 1l22 22',lock:'<rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/>'};
export const TFA_VIEW={NOT_ENABLED:{cls:'off',bd:'danger',text:'Chưa kích hoạt',desc:'Tài khoản quản trị có quyền truy cập dữ liệu nhạy cảm. Bật xác thực hai yếu tố để chặn truy cập trái phép ngay cả khi mật khẩu bị lộ.'},PENDING:{cls:'pending',bd:'warning',text:'Đang chờ xác nhận',desc:'Khóa bí mật đã được tạo. Hãy nhập khóa vào ứng dụng Authenticator trên điện thoại và xác nhận kích hoạt trong ứng dụng để hoàn tất.'},ENABLED:{cls:'on',bd:'success',text:'Đã kích hoạt',desc:'Tài khoản của bạn đang được bảo vệ bằng mã xác thực từ thiết bị.'}};
export const ACC_ST={active:['success','Hoạt động'],pending:['warning','Chờ kích hoạt tài khoản'],disabled:['danger','Vô hiệu hóa'],locked:['danger','Bị khóa']};
export const PW_CHARS='ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789';
export const TFA_ICON = {
  NOT_ENABLED: '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>',
  PENDING: '<circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>',
  ENABLED: '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><polyline points="9 12 11 14 15 10"/>',
}
export const meInit = n => { const w = (n || '').trim().split(/\s+/).filter(Boolean); return w.length ? (w[0][0] + (w.length > 1 ? w[w.length - 1][0] : '')).toUpperCase() : '?' }

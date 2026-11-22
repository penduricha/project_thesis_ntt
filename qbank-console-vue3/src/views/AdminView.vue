<script setup>
import { computed } from 'vue'
import { ACC_ST, ME } from '../data/account.js'
import { S, openDialog } from '../store.js'
import { norm } from '../utils.js'

const rows = computed(() => {
  const q = norm(S.userQuery.trim())
  return S.users.filter(u => (!S.userStatus || u.status === S.userStatus) && (!q || norm(u.name).includes(q) || String(u.adminId).includes(q)))
})
const initials = n => n.trim().split(/\s+/).slice(-2).map(w => w[0]).join('')
</script>

<template>
  <section class="card accounts" aria-labelledby="users-title">
    <header class="card__head">
      <div><p class="directory-kicker">QUẢN LÝ TRUY CẬP</p><h3 id="users-title">Danh sách tài khoản</h3></div>
      <button class="btn pri" @click="openDialog('user-new')">Thêm tài khoản</button>
    </header>
    <div class="directory-tools">
      <label>Tìm tài khoản<input type="search" id="user-search" placeholder="Họ tên hoặc mã đăng nhập" v-model="S.userQuery"></label>
      <label>Trạng thái tài khoản
        <select v-model="S.userStatus"><option value="">Tất cả trạng thái</option><option v-for="(v, k) in ACC_ST" :key="k" :value="k">{{ v[1] }}</option></select>
      </label>
    </div>
    <small id="directory-count" class="mut" role="status">Hiển thị {{ rows.length }} / {{ S.users.length }} tài khoản</small>
    <div class="tw tw--all">
      <table class="tbl-stack" aria-labelledby="users-title">
        <thead><tr><th scope="col">Tài khoản</th><th scope="col">Vai trò</th><th scope="col">Trạng thái tài khoản</th><th scope="col">Thao tác</th></tr></thead>
        <tbody id="directory-rows">
          <tr v-for="u in rows" :key="u.adminId">
            <td><div class="directory-person"><span class="directory-avatar" aria-hidden="true">{{ initials(u.name) }}</span><div><b>{{ u.name }}</b> <span v-if="u.adminId === ME.adminId" class="ubd ubd--primary">Bạn</span><small>Mã · <span class="mono">{{ u.adminId }}</span></small></div></div></td>
            <td data-label="Vai trò">{{ u.role }}</td>
            <td data-label="Trạng thái tài khoản"><span class="ubd" :class="'ubd--' + ACC_ST[u.status][0]">{{ ACC_ST[u.status][1] }}</span></td>
            <td><button class="btn sm" :aria-label="'Chi tiết tài khoản ' + u.name" @click="openDialog('user-detail', { adminId: u.adminId })">Chi tiết</button></td>
          </tr>
        </tbody>
      </table>
    </div>
    <p id="directory-empty" class="policy" :hidden="rows.length > 0">Không tìm thấy tài khoản. Hãy thử tên, mã hoặc trạng thái khác.</p>
  </section>
</template>

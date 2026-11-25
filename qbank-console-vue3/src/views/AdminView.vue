<script setup>
import { computed } from 'vue'
import { ACC_ST, ME } from '../data/account.js'
import SkeletonList from '../components/SkeletonList.vue'
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
      <div><p class="directory-kicker">{{ $t('admin.accessControl') }}</p><h3 id="users-title">{{ $t('admin.accounts') }}</h3></div>
      <button class="btn pri" @click="openDialog('user-new')">{{ $t('admin.addAccount') }}</button>
    </header>
    <div class="directory-tools">
      <label>{{ $t('admin.searchAccount') }}<input type="search" id="user-search" :placeholder="$t('auth.loginIdPlaceholder')" v-model="S.userQuery"></label>
      <label>{{ $t('admin.accountStatus') }}
        <select v-model="S.userStatus"><option value="">{{ $t('bank.allStatus') }}</option><option v-for="(v, k) in ACC_ST" :key="k" :value="k">{{ $t('account.status.' + k) }}</option></select>
      </label>
    </div>
    <!-- Chưa có tài khoản nào -> có thể đang tải từ Spring Boot. Hiện khung xương
         thay vì bảng rỗng (bảng rỗng trông như "không có dữ liệu", gây hiểu nhầm). -->
    <SkeletonList v-if="!S.users.length" variant="table" :rows="4" :label="$t('loading.accounts')" />
    <small id="directory-count" class="mut" role="status">{{ $t('admin.showingCount', { a: rows.length, b: S.users.length }) }}</small>
    <div class="tw tw--all">
      <table class="tbl-stack" aria-labelledby="users-title">
        <thead><tr><th scope="col">{{ $t('nav.accountMenu') }}</th><th scope="col">{{ $t('common.role') }}</th><th scope="col">{{ $t('admin.accountStatus') }}</th><th scope="col">{{ $t('common.actions') }}</th></tr></thead>
        <tbody id="directory-rows">
          <tr v-for="u in rows" :key="u.adminId">
            <td><div class="directory-person"><span class="directory-avatar" aria-hidden="true">{{ initials(u.name) }}</span><div><b>{{ u.name }}</b> <span v-if="u.adminId === ME.adminId" class="ubd ubd--primary">{{ $t('account.you') }}</span><small>{{ $t('brand.db') }} <span class="mono">{{ u.adminId }}</span></small></div></div></td>
            <td :data-label="$t('common.role')">{{ $t(u.role === 'Quản trị viên' ? 'admin.roleAdmin' : 'admin.roleLecturer') }}</td>
            <td :data-label="$t('admin.accountStatus')"><span class="ubd" :class="'ubd--' + ACC_ST[u.status][0]">{{ $t('account.status.' + u.status) }}</span></td>
            <td><button class="btn sm" :aria-label="$t('admin.detailOf') + u.name" @click="openDialog('user-detail', { adminId: u.adminId })">{{ $t('common.detail') }}</button></td>
          </tr>
        </tbody>
      </table>
    </div>
    <p id="directory-empty" class="policy" :hidden="rows.length > 0">{{ $t('admin.noAccount') }}</p>
  </section>
</template>

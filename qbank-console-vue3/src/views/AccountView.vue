<script setup>
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ME, TRAIN_SYS, TRAIN_TYPE, ACI, meInit } from '../data/account.js'
import { S } from '../store.js'
import Ico from '../components/Ico.vue'
import InfoVal from '../components/InfoVal.vue'

const router = useRouter(), c = ME.course
const adm = computed(() => S.role == 'admin'), ld = computed(() => !S.meReady), who = computed(() => (adm.value ? 'quản trị viên' : 'giảng viên'))
const dob = ME.dob.split('-').reverse().join('/')
const shown = computed(() => (S.idShow ? ME.idCard : ME.idCard.slice(0, 6) + '•'.repeat(Math.max(0, ME.idCard.length - 8)) + ME.idCard.slice(-2)))
let idT
function idToggle() { S.idShow = !S.idShow; clearTimeout(idT); if (S.idShow) idT = setTimeout(() => { S.idShow = false }, 30000) }
onMounted(() => { if (!S.meReady) setTimeout(() => { S.meReady = true }, 600) })
</script>

<template>
  <div class="acctpg">
    <div class="pcard">
      <div class="pcard__av" aria-hidden="true">{{ meInit(ME.name) }}</div>
      <div class="pcard__body">
        <div class="pcard__head"><h2 class="pcard__name">{{ ME.name }}</h2><span class="ubd ubd--primary">{{ adm ? 'Quản trị viên' : 'Giảng viên' }}</span></div>
        <dl class="pcard__meta"><div><dt>Mã đăng nhập</dt><dd>{{ ME.adminId }}</dd></div><div><dt>Giới tính</dt><dd>{{ ME.gender ? 'Nam' : 'Nữ' }}</dd></div><div><dt>Ngày sinh</dt><dd>{{ dob }}</dd></div></dl>
      </div>
    </div>

    <article class="icard" aria-labelledby="ai-t" :aria-busy="String(ld)">
      <div class="icard__hdr"><span class="icard__ic"><Ico :d="ACI.user" /></span><div><h3 id="ai-t" class="icard__title">Thông tin {{ who }}</h3><p class="icard__sub">Thông tin cá nhân và quá trình đào tạo</p></div></div>
      <div class="igroup">
        <h4 class="igroup__t"><Ico :d="ACI.user" />Thông tin cá nhân</h4>
        <dl class="igrid">
          <div class="ifield"><dt class="ifield__l"><Ico :d="ACI.pin" />Địa chỉ</dt><dd class="ifield__v"><InfoVal :ld="ld" :v="ME.address" /></dd></div>
          <div class="ifield"><dt class="ifield__l"><Ico :d="ACI.card" />Căn cước công dân</dt>
            <dd class="ifield__v ifield__v--sec">
              <span v-if="ld" class="sk" aria-hidden="true"></span>
              <template v-else>
                <span class="ifield__secret" aria-live="polite">{{ shown }}</span>
                <button type="button" class="btn-rev" :aria-label="(S.idShow ? 'Ẩn' : 'Hiện') + ' số căn cước công dân'" @click="idToggle"><Ico :d="S.idShow ? ACI.eyeOff : ACI.eye" />{{ S.idShow ? 'Ẩn' : 'Hiện' }}</button>
                <span v-if="S.idShow" class="ifield__hint">Tự động ẩn sau 30 giây</span>
              </template>
            </dd>
          </div>
        </dl>
      </div>
      <div class="igroup">
        <h4 class="igroup__t"><Ico :d="ACI.book" />Thông tin đào tạo</h4>
        <dl class="igrid igrid--3">
          <div class="ifield"><dt class="ifield__l">Khoa</dt><dd class="ifield__v"><InfoVal :ld="ld" :v="c.faculty" /></dd></div>
          <div class="ifield"><dt class="ifield__l">Ngành</dt><dd class="ifield__v"><InfoVal :ld="ld" :v="c.industry" /></dd></div>
          <div class="ifield"><dt class="ifield__l">Chuyên ngành</dt><dd class="ifield__v"><InfoVal :ld="ld" :v="c.major" /></dd></div>
          <div class="ifield"><dt class="ifield__l">Hệ đào tạo</dt><dd class="ifield__v"><InfoVal :ld="ld" :v="TRAIN_SYS[c.trainingSystem] || c.trainingSystem" badge="primary" /></dd></div>
          <div class="ifield"><dt class="ifield__l">Loại hình đào tạo</dt><dd class="ifield__v"><InfoVal :ld="ld" :v="TRAIN_TYPE[c.typeOfTraining] || c.typeOfTraining" badge="accent" /></dd></div>
          <div class="ifield"><dt class="ifield__l">Cơ sở</dt><dd class="ifield__v"><InfoVal :ld="ld" :v="c.branch" /></dd></div>
        </dl>
      </div>
    </article>
    <p class="mut" style="margin:0;font-size:var(--fs-sm)">Xác thực hai yếu tố được quản lý tại <button type="button" class="btn-rev" @click="router.push('/settings')">Cài đặt</button></p>
  </div>
</template>

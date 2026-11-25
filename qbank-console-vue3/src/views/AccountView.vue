<script setup>
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ME, TRAIN_SYS, TRAIN_TYPE, ACI, meInit } from '../data/account.js'
import { S } from '../store.js'
import { auth } from '../auth/authStore.js'
import Ico from '../components/Ico.vue'
import InfoVal from '../components/InfoVal.vue'

const router = useRouter()
// Ưu tiên dữ liệu thật từ GET /information/basic + /information/detail; chưa có thì dùng ME (demo).
const me = computed(() => {
  const b = auth.basic, d = auth.detail
  if (!b && !d) return ME
  return {
    adminId: b?.adminId ?? ME.adminId,
    name: b?.adminName || ME.name,
    gender: typeof b?.gender === 'boolean' ? b.gender : ME.gender,
    dob: b?.dateOfBirthAdmin || ME.dob,
    address: d?.address ?? ME.address,
    idCard: d?.idCard ?? ME.idCard,
    course: {
      faculty: d?.faculty ?? '', industry: d?.industry ?? '', major: d?.major ?? '',
      trainingSystem: d?.trainingSystem ?? '', typeOfTraining: d?.typeOfTraining ?? '',
      branch: d?.branch ?? '',
    },
  }
})
const c = computed(() => me.value.course || {})
const adm = computed(() => S.role === 'admin'), ld = computed(() => !S.meReady), who = computed(() => (adm.value ? 'quản trị viên' : 'giảng viên'))
const dob = computed(() => (me.value.dob ? String(me.value.dob).split('-').reverse().join('/') : '—'))
const shown = computed(() => (S.idShow ? me.value.idCard : me.value.idCard.slice(0, 6) + '•'.repeat(Math.max(0, me.value.idCard.length - 8)) + me.value.idCard.slice(-2)))
let idT
function idToggle() { S.idShow = !S.idShow; clearTimeout(idT); if (S.idShow) idT = setTimeout(() => { S.idShow = false }, 30000) }
onMounted(() => { if (!S.meReady) setTimeout(() => { S.meReady = true }, 600) })
</script>

<template>
  <div class="acctpg">
    <div class="pcard">
      <div class="pcard__av" aria-hidden="true">{{ meInit(me.name) }}</div>
      <div class="pcard__body">
        <div class="pcard__head"><h2 class="pcard__name">{{ me.name }}</h2><span class="ubd ubd--primary">{{ adm ? $t('admin.roleAdmin') : $t('admin.roleLecturer') }}</span></div>
        <dl class="pcard__meta"><div><dt>{{ $t('auth.loginId') }}</dt><dd>{{ me.adminId }}</dd></div><div><dt>{{ $t('account.gender') }}</dt><dd>{{ me.gender ? $t('account.male') : $t('account.female') }}</dd></div><div><dt>{{ $t('account.dob') }}</dt><dd>{{ dob }}</dd></div></dl>
      </div>
    </div>

    <article class="icard" aria-labelledby="ai-t" :aria-busy="String(ld)">
      <div class="icard__hdr"><span class="icard__ic"><Ico :d="ACI.user" /></span><div><h3 id="ai-t" class="icard__title">{{ $t('account.infoOf', { who }) }}</h3><p class="icard__sub">{{ $t('account.personalDesc') }}</p></div></div>
      <div class="igroup">
        <h4 class="igroup__t"><Ico :d="ACI.user" />{{ $t('account.personal') }}</h4>
        <dl class="igrid">
          <div class="ifield"><dt class="ifield__l"><Ico :d="ACI.pin" />{{ $t('common.url') }}</dt><dd class="ifield__v"><InfoVal :ld="ld" :v="me.address" /></dd></div>
          <div class="ifield"><dt class="ifield__l"><Ico :d="ACI.card" />{{ $t('account.idCard') }}</dt>
            <dd class="ifield__v ifield__v--sec">
              <span v-if="ld" class="sk" aria-hidden="true"></span>
              <template v-else>
                <span class="ifield__secret" aria-live="polite">{{ shown }}</span>
                <button type="button" class="btn-rev" :aria-label="(S.idShow ? $t('common.hide') : $t('common.show')) + $t('account.idCardShow')" @click="idToggle"><Ico :d="S.idShow ? ACI.eyeOff : ACI.eye" />{{ S.idShow ? $t('common.hide') : $t('common.show') }}</button>
                <span v-if="S.idShow" class="ifield__hint">{{ $t('dialog.toastAutoHide') }}</span>
              </template>
            </dd>
          </div>
        </dl>
      </div>
      <div class="igroup">
        <h4 class="igroup__t"><Ico :d="ACI.book" />{{ $t('account.training') }}</h4>
        <dl class="igrid igrid--3">
          <div class="ifield"><dt class="ifield__l">{{ $t('course.dept') }}</dt><dd class="ifield__v"><InfoVal :ld="ld" :v="c.faculty" /></dd></div>
          <div class="ifield"><dt class="ifield__l">{{ $t('account.industry') }}</dt><dd class="ifield__v"><InfoVal :ld="ld" :v="c.industry" /></dd></div>
          <div class="ifield"><dt class="ifield__l">{{ $t('account.major') }}</dt><dd class="ifield__v"><InfoVal :ld="ld" :v="c.major" /></dd></div>
          <div class="ifield"><dt class="ifield__l">{{ $t('account.trainingSystem') }}</dt><dd class="ifield__v"><InfoVal :ld="ld" :v="TRAIN_SYS[c.trainingSystem] || c.trainingSystem" badge="primary" /></dd></div>
          <div class="ifield"><dt class="ifield__l">{{ $t('account.trainingType') }}</dt><dd class="ifield__v"><InfoVal :ld="ld" :v="TRAIN_TYPE[c.typeOfTraining] || c.typeOfTraining" badge="accent" /></dd></div>
          <div class="ifield"><dt class="ifield__l">{{ $t('account.branch') }}</dt><dd class="ifield__v"><InfoVal :ld="ld" :v="c.branch" /></dd></div>
        </dl>
      </div>
    </article>
    <p class="mut" style="margin:0;font-size:var(--fs-sm)">{{ $t('tfa.managedAt') }} <button type="button" class="btn-rev" @click="router.push('/settings')">{{ $t('nav.settings') }}</button></p>
  </div>
</template>

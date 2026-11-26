<script setup>
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ME, TRAIN_SYS, TRAIN_TYPE, ACI, meInit } from '../data/account.js'
import { S } from '../store.js'
import { t } from '../i18n/index.js'
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
const adm = computed(() => S.role === 'admin'), ld = computed(() => !S.meReady), who = computed(() => t(adm.value ? 'admin.roleAdmin' : 'admin.roleLecturer'))
const dob = computed(() => (me.value.dob ? String(me.value.dob).split('-').reverse().join('/') : '—'))
const shown = computed(() => (S.idShow ? me.value.idCard : me.value.idCard.slice(0, 6) + '•'.repeat(Math.max(0, me.value.idCard.length - 8)) + me.value.idCard.slice(-2)))
let idT
function idToggle() { S.idShow = !S.idShow; clearTimeout(idT); if (S.idShow) idT = setTimeout(() => { S.idShow = false }, 30000) }
onMounted(() => { if (!S.meReady) setTimeout(() => { S.meReady = true }, 600) })
</script>

<template>
  <div class="card set account" :aria-busy="String(ld)">
    <header class="account__profile">
      <div class="account__identity">
        <div class="account__avatar" aria-hidden="true">{{ meInit(me.name) }}</div>
        <div class="account__identity-text">
          <div class="account__name-line">
            <h2 class="account__name">{{ me.name }}</h2>
            <span class="ubd ubd--primary">{{ who }}</span>
          </div>
          <p class="account__subtitle">{{ $t('account.personalDesc') }}</p>
        </div>
      </div>
      <dl class="account__summary">
        <div><dt>{{ $t('auth.loginId') }}</dt><dd>{{ me.adminId }}</dd></div>
        <div><dt>{{ $t('account.gender') }}</dt><dd>{{ me.gender ? $t('account.male') : $t('account.female') }}</dd></div>
        <div><dt>{{ $t('account.dob') }}</dt><dd>{{ dob }}</dd></div>
      </dl>
    </header>

    <section class="set__row account__row" aria-labelledby="account-personal-title">
      <div class="set__intro">
        <h3 id="account-personal-title" class="set__t">{{ $t('account.personal') }}</h3>
        <p class="set__d">{{ $t('account.infoOf', { who }) }}</p>
      </div>
      <dl class="account__fields account__fields--personal">
        <div class="account__field">
          <dt><Ico :d="ACI.pin" />{{ $t('account.address') }}</dt>
          <dd><InfoVal :ld="ld" :v="me.address" /></dd>
        </div>
        <div class="account__field">
          <dt><Ico :d="ACI.card" />{{ $t('account.idCard') }}</dt>
          <dd class="account__secret-row">
            <span v-if="ld" class="sk" aria-hidden="true"></span>
            <template v-else>
              <span class="account__secret" aria-live="polite">{{ shown }}</span>
              <button type="button" class="btn-rev" :aria-label="(S.idShow ? $t('common.hide') : $t('common.show')) + $t('account.idCardShow')" @click="idToggle"><Ico :d="S.idShow ? ACI.eyeOff : ACI.eye" />{{ S.idShow ? $t('common.hide') : $t('common.show') }}</button>
              <span v-if="S.idShow" class="account__hint">{{ $t('dialog.toastAutoHide') }}</span>
            </template>
          </dd>
        </div>
      </dl>
    </section>

    <section class="set__row account__row" aria-labelledby="account-training-title">
      <div class="set__intro">
        <h3 id="account-training-title" class="set__t">{{ $t('account.training') }}</h3>
        <p class="set__d">{{ $t('account.personalDesc') }}</p>
      </div>
      <dl class="account__fields">
        <div class="account__field"><dt>{{ $t('course.dept') }}</dt><dd><InfoVal :ld="ld" :v="c.faculty" /></dd></div>
        <div class="account__field"><dt>{{ $t('account.industry') }}</dt><dd><InfoVal :ld="ld" :v="c.industry" /></dd></div>
        <div class="account__field"><dt>{{ $t('account.major') }}</dt><dd><InfoVal :ld="ld" :v="c.major" /></dd></div>
        <div class="account__field"><dt>{{ $t('account.trainingSystem') }}</dt><dd><InfoVal :ld="ld" :v="TRAIN_SYS[c.trainingSystem] ? $t('training.system.' + (c.trainingSystem === 'PhD' ? 'Doctor' : c.trainingSystem)) : c.trainingSystem" badge="primary" /></dd></div>
        <div class="account__field"><dt>{{ $t('account.trainingType') }}</dt><dd><InfoVal :ld="ld" :v="TRAIN_TYPE[c.typeOfTraining] ? $t('training.type.' + c.typeOfTraining) : c.typeOfTraining" badge="accent" /></dd></div>
        <div class="account__field"><dt>{{ $t('account.branch') }}</dt><dd><InfoVal :ld="ld" :v="c.branch" /></dd></div>
      </dl>
    </section>

    <section class="set__row account__row" aria-labelledby="account-security-title">
      <div class="set__intro"><h3 id="account-security-title" class="set__t">{{ $t('auth.otpTitle') }}</h3></div>
      <div class="account__security">
        <span class="account__security-icon" aria-hidden="true"><Ico :d="ACI.lock" /></span>
        <div class="account__security-body"><p>{{ $t('tfa.managedAt') }}</p><button type="button" class="btn sm" @click="router.push('/settings')">{{ $t('nav.settings') }}</button></div>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* Match the single-card, divided-row layout used by SettingsView. */
.account { width: 100%; }
.account__profile { display: grid; gap: 1.25rem; padding: var(--space-3) var(--space-2); background: var(--surface); border-radius: 12px 12px 0 0; }
.account__identity { display: flex; align-items: center; gap: 1rem; min-width: 0; }
.account__avatar { display: grid; place-items: center; flex: none; width: 3.75rem; height: 3.75rem; border-radius: 50%; background: var(--primary-fill); color: var(--on-primary); font-size: 1.25rem; font-weight: 700; }
.account__identity-text { min-width: 0; }
.account__name-line { display: flex; align-items: center; flex-wrap: wrap; gap: .4rem .65rem; }
.account__name { margin: 0; font-size: 1.25rem; line-height: 1.35; overflow-wrap: anywhere; }
.account__subtitle { margin: .3rem 0 0; color: var(--text-muted); font-size: var(--fs-sm); line-height: 1.5; }
.account__summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 9rem), 1fr)); gap: 1rem; margin: 0; padding-top: 1.1rem; border-top: 1px solid var(--border); }
.account__summary div { min-width: 0; }
.account__summary dt, .account__field dt { color: var(--text-muted); font-size: var(--fs-xs); font-weight: 500; }
.account__summary dd, .account__field dd { margin: .35rem 0 0; color: var(--text); font-weight: 600; line-height: 1.5; overflow-wrap: anywhere; }
.account__row { border-top: 1px solid var(--border); }
.account__fields { display: grid; grid-template-columns: minmax(0, 1fr); gap: .65rem; min-width: 0; margin: 0; align-content: start; }
.account__field { min-width: 0; padding: .85rem 1rem; border: 1px solid var(--border); border-radius: var(--radius); background: var(--surface-hover); }
.account__field dt { display: flex; align-items: center; gap: .4rem; }
.account__field dt svg { width: 1rem; height: 1rem; flex: none; color: var(--primary-ink); }
.account__secret-row { display: flex; align-items: center; flex-wrap: wrap; gap: .4rem .65rem; }
.account__secret { font-variant-numeric: tabular-nums; letter-spacing: .06em; }
.account__hint { flex-basis: 100%; color: var(--text-muted); font-size: var(--fs-xs); font-weight: 400; }
.account__security { display: flex; align-items: flex-start; gap: .8rem; min-width: 0; padding: 1rem; border: 1px solid var(--border); border-radius: var(--radius); background: var(--surface-hover); }
.account__security-icon { display: grid; place-items: center; flex: none; width: 2.5rem; height: 2.5rem; border-radius: var(--radius); background: var(--primary-soft); color: var(--primary-ink); }
.account__security-icon svg { width: 1.2rem; height: 1.2rem; }
.account__security-body { display: grid; justify-items: start; gap: .65rem; min-width: 0; }
.account__security-body p { margin: 0; color: var(--text-muted); font-size: var(--fs-sm); line-height: 1.5; }
@container set (min-width: 40rem) {
  .account__profile { padding: var(--space-4); }
  .account__fields { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>

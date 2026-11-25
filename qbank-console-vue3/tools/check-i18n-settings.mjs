import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createSSRApp, computed } from 'vue'
import { renderToString } from 'vue/server-renderer'

const storage = new Map([['qbank-locale', 'es-ES']])
const attributes = new Map()
globalThis.localStorage = { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value) }
globalThis.document = { documentElement: { getAttribute: key => attributes.get(key) ?? null, setAttribute: (key, value) => attributes.set(key, value), style: {} } }
const server = await createServer({ server: { middlewareMode: true }, appType: 'custom', logLevel: 'error' })
try {
  const { default: Settings } = await server.ssrLoadModule('/src/views/SettingsView.vue')
  const { default: UserCreateDialog } = await server.ssrLoadModule('/src/components/UserCreateDialog.vue')
  const { default: UserDetailDialog } = await server.ssrLoadModule('/src/components/UserDetailDialog.vue')
  const { S } = await server.ssrLoadModule('/src/store.js')
  const { i18n, setLocale, t, missingKeys, locale, LOCALES } = await server.ssrLoadModule('/src/i18n/index.js')
  assert.equal(locale.code, 'es-ES', 'restores Spanish from storage')
  assert.ok(LOCALES.some(l => l.code === 'es-ES' && l.label === 'Español (España)'))
  const reactiveLabel = computed(() => t('settings.themeLight'))
  const expected = {
    vi: ['Sáng', 'Thông báo khi tác vụ AI hoàn tất'],
    en: ['Light', 'Notify when an AI task finishes'],
    zh: ['浅色', 'AI'], ko: ['밝게', 'AI'], ja: ['ライト', 'AI'],
    'es-ES': ['Claro', 'Avisar cuando termine una tarea de IA'],
  }
  assert.deepEqual(missingKeys(), {})
  const localeTables = await Promise.all(['vi', 'en', 'zh', 'ko', 'ja', 'es-ES'].map(code => import(`../src/i18n/locales/${code === 'es-ES' ? 'es' : code}.js`).then(m => [code, m.default])))
  const base = localeTables[0][1]
  const params = text => (text.match(/\{\w+\}/g) || []).sort().join(',')
  for (const [code, table] of localeTables) {
    assert.deepEqual(Object.keys(table).sort(), Object.keys(base).sort(), `${code}: locale keys differ`)
    for (const key of Object.keys(base)) assert.equal(params(table[key]), params(base[key]), `${code}: parameters for ${key}`)
  }
  for (const [language, [theme, notification]] of Object.entries(expected)) {
    setLocale(language)
    assert.equal(storage.get('qbank-locale'), language)
    assert.equal(attributes.get('lang'), language === 'zh' ? 'zh-CN' : language)
    const html = await renderToString(createSSRApp(Settings).use(i18n))
    const userContext = {}
    await renderToString(createSSRApp(UserCreateDialog).use(i18n), userContext)
    const dialog = userContext.teleports.body
    assert.match(dialog, /value="Giảng viên"/u, `${language}: stable lecturer value`)
    assert.match(dialog, /value="Quản trị viên"/u, `${language}: stable administrator value`)
    assert.ok(dialog.includes(t('admin.roleLecturer')), `${language}: lecturer label`)
    assert.ok(dialog.includes(t('admin.roleAdmin')), `${language}: administrator label`)
    const detailsContext = {}
    await renderToString(createSSRApp(UserDetailDialog, { adminId: S.users[2].adminId }).use(i18n), detailsContext)
    assert.ok(detailsContext.teleports.body.includes(t('admin.roleLecturer')), `${language}: account detail role`)
    assert.ok(detailsContext.teleports.body.includes(t('account.status.active')), `${language}: account status`)
    assert.ok(html.includes('Español (España)'), `${language}: Spanish option missing`)
    assert.ok(html.includes(theme), `${language}: theme label missing`)
    assert.ok(html.includes(notification), `${language}: notification label missing`)
    assert.equal(reactiveLabel.value, t('settings.themeLight'))
    assert.ok(!html.includes('tfa.state.'), `${language}: untranslated key`)
    assert.ok(!html.includes('settings.themeLight'), `${language}: untranslated key`)
    for (const state of ['NOT_ENABLED', 'PENDING', 'ENABLED']) {
      S.tfa.st = state
      const statusHtml = await renderToString(createSSRApp(Settings).use(i18n))
      assert.ok(statusHtml.includes(t('tfa.state.' + state)), `${language}: ${state} status`)
      assert.ok(statusHtml.includes(t('tfa.description.' + state)), `${language}: ${state} description`)
    }
    S.tfa.st = 'NOT_ENABLED'
  }
  console.log('Settings and account dialogs: 6 locales rendered successfully')
} catch (error) {
  console.error(error)
  process.exitCode = 1
} finally {
  await server.close()
}
process.exit(process.exitCode || 0)

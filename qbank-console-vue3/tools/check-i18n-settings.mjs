import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createSSRApp, computed } from 'vue'
import { renderToString } from 'vue/server-renderer'

globalThis.document = { documentElement: { getAttribute: () => null, setAttribute() {}, style: {} } }
const server = await createServer({ server: { middlewareMode: true }, appType: 'custom', logLevel: 'error' })
try {
  const { default: Settings } = await server.ssrLoadModule('/src/views/SettingsView.vue')
  const { S } = await server.ssrLoadModule('/src/store.js')
  const { i18n, setLocale, t, missingKeys } = await server.ssrLoadModule('/src/i18n/index.js')
  const reactiveLabel = computed(() => t('settings.themeLight'))
  const expected = {
    vi: ['Sáng', 'Thông báo khi tác vụ AI hoàn tất'],
    en: ['Light', 'Notify when an AI task finishes'],
    zh: ['浅色', 'AI'], ko: ['밝게', 'AI'], ja: ['ライト', 'AI'],
  }
  assert.deepEqual(missingKeys(), {})
  for (const [language, [theme, notification]] of Object.entries(expected)) {
    setLocale(language)
    const html = await renderToString(createSSRApp(Settings).use(i18n))
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
  console.log('Settings: 5 locales rendered successfully')
} catch (error) {
  console.error(error)
  process.exitCode = 1
} finally {
  await server.close()
}
process.exit(process.exitCode || 0)

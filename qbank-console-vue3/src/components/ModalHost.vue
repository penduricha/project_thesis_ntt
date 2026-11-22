<script setup>
import { S, closeDialog } from '../store.js'
import ConfirmDialog from './ConfirmDialog.vue'
import QuestionDialog from './QuestionDialog.vue'
import CourseDialog from './CourseDialog.vue'
import TopicManagerDialog from './TopicManagerDialog.vue'
import KeysDialog from './KeysDialog.vue'
import ImportDialog from './ImportDialog.vue'
import TfaDialog from './TfaDialog.vue'
import TempPasswordDialog from './TempPasswordDialog.vue'
import UserConfirmDialog from './UserConfirmDialog.vue'
import UserDetailDialog from './UserDetailDialog.vue'
import UserCreateDialog from './UserCreateDialog.vue'
const map = { confirm: ConfirmDialog, question: QuestionDialog, course: CourseDialog, topics: TopicManagerDialog, keys: KeysDialog, import: ImportDialog, tfa: TfaDialog, pw: TempPasswordDialog, 'user-confirm': UserConfirmDialog, 'user-detail': UserDetailDialog, 'user-new': UserCreateDialog }
</script>
<template>
  <component :is="map[S.dlg.kind]" v-if="S.dlg.kind" :key="S.dlg.n" v-bind="S.dlg.props" @close="closeDialog" />
</template>

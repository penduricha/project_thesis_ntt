<script setup>
import { S, closeDialog } from '../store.js'
import ConfirmDialog from './ConfirmDialog.vue'
import QuestionDialog from './QuestionDialog.vue'
import CourseDialog from './CourseDialog.vue'
import TopicManagerDialog from './TopicManagerDialog.vue'
const map = { confirm: ConfirmDialog, question: QuestionDialog, course: CourseDialog, topics: TopicManagerDialog }
</script>
<template>
  <component :is="map[S.dlg.kind]" v-if="S.dlg.kind" :key="S.dlg.n" v-bind="S.dlg.props" @close="closeDialog" />
</template>

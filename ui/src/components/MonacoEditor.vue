<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { monaco } from '../lib/monaco'

const props = withDefaults(
  defineProps<{
    value: string
    language?: string
    readOnly?: boolean
  }>(),
  {
    language: 'json',
    readOnly: true,
  },
)

const host = ref<HTMLDivElement | null>(null)
let editor: monaco.editor.IStandaloneCodeEditor | null = null

onMounted(() => {
  if (!host.value) return
  editor = monaco.editor.create(host.value, {
    value: props.value,
    language: props.language,
    readOnly: props.readOnly,
    theme: 'vs-dark',
    automaticLayout: true,
    minimap: { enabled: false },
    fontSize: 12,
    lineNumbers: 'on',
    scrollBeyondLastLine: false,
    wordWrap: 'on',
    tabSize: 2,
  })
})

watch(
  () => props.value,
  (value) => {
    if (editor && editor.getValue() !== value) {
      editor.setValue(value)
    }
  },
)

watch(
  () => props.language,
  (language) => {
    if (editor?.getModel()) {
      monaco.editor.setModelLanguage(editor.getModel()!, language ?? 'json')
    }
  },
)

onBeforeUnmount(() => {
  editor?.dispose()
  editor = null
})
</script>

<template>
  <div ref="host" class="monaco-host" />
</template>

<style scoped>
.monaco-host {
  width: 100%;
  height: 100%;
  min-height: 120px;
}
</style>

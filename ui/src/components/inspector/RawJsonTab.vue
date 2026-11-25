<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { usePdfStore } from '../../stores/pdf'
import MonacoEditor from '../MonacoEditor.vue'

const store = usePdfStore()

type Source = { key: string; label: string; data: () => unknown }

const sources = computed<Source[]>(() => {
  const list: Source[] = [
    { key: 'metadata', label: 'Metadata', data: () => store.metadata },
    { key: 'signatures', label: 'Signatures', data: () => store.signatures },
    { key: 'widgets', label: 'Widgets', data: () => store.widgets },
    { key: 'certificates', label: 'Certificates', data: () => store.certificates },
    { key: 'chains', label: 'Certificate chains', data: () => store.chains },
    { key: 'validation', label: 'Validation report', data: () => store.validation },
    { key: 'revisions', label: 'Incremental revisions', data: () => store.revisions },
  ]
  if (store.cms) {
    list.push({
      key: `cms:${store.selectedSignatureIndex ?? 0}`,
      label: `CMS (signature ${(store.selectedSignatureIndex ?? 0) + 1})`,
      data: () => store.cms,
    })
  }
  if (store.asn1) {
    list.push({
      key: `asn1:${store.selectedSignatureIndex ?? 0}`,
      label: `ASN.1 tree (signature ${(store.selectedSignatureIndex ?? 0) + 1})`,
      data: () => store.asn1,
    })
  }
  return list
})

const selectedKey = ref('metadata')

const jsonText = computed(() => {
  const source = sources.value.find((s) => s.key === selectedKey.value)
  if (!source) return '{}'
  const data = source.data()
  if (data === null || data === undefined) return 'null'
  return JSON.stringify(data, null, 2)
})

watch(
  sources,
  (list) => {
    if (!list.some((s) => s.key === selectedKey.value)) {
      selectedKey.value = list[0]?.key ?? 'metadata'
    }
  },
  { deep: true },
)
function copyJson() {
  void navigator.clipboard?.writeText(jsonText.value)
}
</script>

<template>
  <div class="raw-json-tab">
    <div class="json-toolbar">
      <label class="text-muted" for="json-source">Response</label>
      <select id="json-source" v-model="selectedKey" class="dt-select">
        <option v-for="s in sources" :key="s.key" :value="s.key">{{ s.label }}</option>
      </select>
      <span class="toolbar-spacer" />
      <button class="dt-btn" title="Copy JSON" @click="copyJson">
        <i class="pi pi-copy" />
      </button>
    </div>
    <div class="json-editor">
      <MonacoEditor :value="jsonText" language="json" />
    </div>
  </div>
</template>

<style scoped>
.raw-json-tab {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.json-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--dt-border);
  flex-shrink: 0;
}

.json-editor {
  flex: 1;
  min-height: 0;
}
</style>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { usePdfStore } from '../stores/pdf'
import { formatBytes } from '../lib/format'

const store = usePdfStore()
const router = useRouter()

const metadata = computed(() => store.metadata)
const revisions = computed(() => store.revisions)

const revisionOptions = computed(() => [
  { label: 'Current file', value: null as number | null },
  ...revisions.value.map((r) => ({
    label: `Revision ${r.index + 1} — ${r.signerNames.join(', ') || r.description}`,
    value: r.index as number | null,
  })),
])

function onRevisionChange(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  store.selectRevision(value === '' ? null : Number(value))
}

function goHome() {
  store.clear()
  void router.push('/')
}
</script>

<template>
  <header class="dt-toolbar">
    <span class="pi pi-file" style="color: var(--dt-accent)" />
    <span class="toolbar-title">PDF Signature Inspector</span>
    <span v-if="metadata" class="toolbar-title text-muted" style="font-weight: 400">
      <template v-if="metadata.filename">· {{ metadata.filename }}</template>
      <template v-if="metadata.pageCount"> · {{ metadata.pageCount }} pages</template>
      <template v-if="metadata.signatureCount"> · {{ metadata.signatureCount }} signature{{ metadata.signatureCount > 1 ? 's' : '' }}</template>
      <template v-if="metadata.fileSize"> · {{ formatBytes(metadata.fileSize) }}</template>
    </span>
    <span class="toolbar-spacer" />
    <template v-if="revisionOptions.length > 1">
      <label class="text-muted" for="revision-select" style="font-size: 12px">Revision</label>
      <select id="revision-select" class="dt-select" :value="store.activeRevisionIndex ?? ''" @change="onRevisionChange">
        <option v-for="opt in revisionOptions" :key="String(opt.value)" :value="opt.value ?? ''">
          {{ opt.label }}
        </option>
      </select>
    </template>
    <button class="dt-btn" title="Open another PDF" @click="goHome">
      <i class="pi pi-upload" />
      Open
    </button>
  </header>
</template>

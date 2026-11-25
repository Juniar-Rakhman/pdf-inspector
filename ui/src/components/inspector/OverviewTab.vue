<script setup lang="ts">
import { computed } from 'vue'
import { usePdfStore } from '../../stores/pdf'
import StatusBadge from '../StatusBadge.vue'
import { formatBytes, formatDate } from '../../lib/format'

const store = usePdfStore()

const metadata = computed(() => store.metadata)
const summary = computed(() => store.validation?.summary)

const signatureSummaries = computed(() =>
  (store.validation?.signatures ?? []).map((v) => ({
    index: v.signatureIndex,
    signerName: v.signerName || store.signatures.find((s) => s.index === v.signatureIndex)?.signerName || `Signature ${v.signatureIndex + 1}`,
    signatureIntegrity: v.signatureIntegrity,
    documentIntegrity: v.documentIntegrity,
  })),
)
</script>

<template>
  <div class="tab-body">
    <div v-if="store.isBusy('metadata')" class="text-muted tab-hint">Loading metadata…</div>
    <template v-else-if="metadata">
      <div v-if="summary" class="summary-box">{{ summary }}</div>

      <section class="dt-card">
        <div class="dt-card-header">
          <i class="pi pi-file" />
          Document
        </div>
        <table class="dt-kv">
          <tbody>
            <tr><th>Filename</th><td>{{ metadata.filename }}</td></tr>
            <tr><th>Size</th><td>{{ formatBytes(metadata.fileSize) }} <span class="text-muted">({{ metadata.fileSize }} bytes)</span></td></tr>
            <tr><th>Pages</th><td>{{ metadata.pageCount }}</td></tr>
            <tr><th>Signatures</th><td>{{ metadata.signatureCount }}</td></tr>
            <tr><th>Incremental revisions</th><td>{{ metadata.revisionCount }}</td></tr>
            <tr><th>Title</th><td>{{ metadata.title ?? '—' }}</td></tr>
            <tr><th>Author</th><td>{{ metadata.author ?? '—' }}</td></tr>
            <tr><th>Producer</th><td>{{ metadata.producer ?? '—' }}</td></tr>
            <tr><th>Analyzed at</th><td>{{ formatDate(metadata.createdAt) }}</td></tr>
            <tr><th>Document id</th><td class="mono">{{ metadata.documentId }}</td></tr>
          </tbody>
        </table>
      </section>

      <section class="dt-card">
        <div class="dt-card-header">
          <i class="pi pi-hashtag" />
          Hashes
        </div>
        <table class="dt-kv">
          <tbody>
            <tr><th>SHA-256</th><td class="mono">{{ metadata.sha256 }}</td></tr>
            <tr><th>MD5</th><td class="mono">{{ metadata.md5 }}</td></tr>
          </tbody>
        </table>
      </section>

      <section class="dt-card">
        <div class="dt-card-header">
          <i class="pi pi-check-circle" />
          Signature summary
        </div>
        <table class="dt-kv">
          <tbody>
            <tr>
              <th>Signer</th>
              <th>Signature integrity</th>
              <th>Document integrity</th>
            </tr>
            <tr v-for="s in signatureSummaries" :key="s.index">
              <td>
                <button class="link-btn" @click="store.selectSignature(s.index)">
                  {{ s.signerName }}
                </button>
              </td>
              <td><StatusBadge :status="s.signatureIntegrity" /></td>
              <td><StatusBadge :status="s.documentIntegrity" /></td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>

    <div v-else-if="store.errors.length" class="error-panel">{{ store.errors.map((e) => e.message).join('\n') }}</div>
  </div>
</template>

<style scoped>
.tab-body {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.summary-box {
  background: var(--dt-accent-bg);
  border: 1px solid rgba(138, 180, 248, 0.35);
  color: var(--dt-text);
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 12px;
}

.tab-hint {
  padding: 12px;
}

.link-btn {
  background: none;
  border: none;
  color: var(--dt-accent);
  font-size: 12px;
  cursor: pointer;
  padding: 0;
  font-family: inherit;
  text-align: left;
}

.link-btn:hover {
  text-decoration: underline;
}
</style>

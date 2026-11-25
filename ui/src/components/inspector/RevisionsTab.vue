<script setup lang="ts">
import { computed } from 'vue'
import { usePdfStore } from '../../stores/pdf'
import { formatBytes } from '../../lib/format'

const store = usePdfStore()

const revisions = computed(() => store.revisions)
const activeIndex = computed(() => store.activeRevisionIndex)

function select(index: number | null) {
  store.selectRevision(index)
}
</script>

<template>
  <div class="tab-body">
    <div v-if="revisions.length === 0" class="text-muted tab-hint">
      No incremental revisions detected (single revision file).
    </div>

    <div v-else class="timeline">
      <div
        v-for="rev in [...revisions].reverse()"
        :key="rev.index"
        class="timeline-item"
        :class="{ active: activeIndex === rev.index }"
      >
        <div class="timeline-line">
          <span class="timeline-dot" />
        </div>
        <div class="revision-card">
          <div class="revision-head">
            <span class="revision-title">Revision {{ rev.index + 1 }}</span>
            <span v-if="rev.isLast" class="status-badge ok">latest</span>
            <span v-if="activeIndex === rev.index" class="status-badge ok">viewing</span>
            <span v-else class="status-badge unknown">original</span>
          </div>
          <div class="revision-desc">{{ rev.description }}</div>
          <table class="dt-kv">
            <tbody>
              <tr><th>Byte range</th><td class="mono">{{ rev.startOffset }} → {{ rev.endOffset }} ({{ formatBytes(rev.endOffset - rev.startOffset) }})</td></tr>
              <tr><th>Xref offset</th><td class="mono">{{ rev.xrefOffset }}</td></tr>
              <tr v-if="rev.signatureIndices.length">
                <th>Signatures introduced</th>
                <td>
                  <div v-for="s in rev.signatureIndices" :key="s" class="mono">
                    Signature {{ s + 1 }} — {{ rev.signerNames[rev.signatureIndices.indexOf(s)] ?? 'unknown signer' }}
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
          <div class="revision-actions">
            <button
              v-if="activeIndex !== rev.index"
              class="dt-btn"
              @click="select(rev.index)"
            >
              <i class="pi pi-eye" /> View this revision
            </button>
            <button v-else class="dt-btn active" @click="select(null)">
              <i class="pi pi-refresh" /> Show current file
            </button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="activeIndex !== null" class="revision-note">
      <i class="pi pi-info-circle" />
      The PDF viewer is showing revision {{ activeIndex + 1 }}. Signatures and analysis below always refer to the full current file.
    </div>
  </div>
</template>

<style scoped>
.tab-body {
  padding: 12px;
  overflow: auto;
}

.timeline {
  display: flex;
  flex-direction: column;
}

.timeline-item {
  display: flex;
  gap: 12px;
}

.timeline-line {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 16px;
  flex-shrink: 0;
}

.timeline-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--dt-bg-elevated);
  border: 2px solid var(--dt-border-strong);
  margin-top: 14px;
  flex-shrink: 0;
}

.timeline-item.active .timeline-dot {
  background: var(--dt-accent);
  border-color: var(--dt-accent);
}

.revision-card {
  flex: 1;
  background: var(--dt-bg-panel);
  border: 1px solid var(--dt-border);
  border-radius: 6px;
  margin-bottom: 14px;
  overflow: hidden;
}

.timeline-item.active .revision-card {
  border-color: var(--dt-accent);
}

.revision-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: var(--dt-bg-elevated);
  border-bottom: 1px solid var(--dt-border);
}

.revision-title {
  font-weight: 600;
  font-size: 12px;
}

.revision-desc {
  padding: 8px 12px;
  font-size: 12px;
  color: var(--dt-text-muted);
}

.revision-actions {
  padding: 8px 12px;
  border-top: 1px solid var(--dt-border);
}

.revision-note {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--dt-accent-bg);
  border: 1px solid rgba(138, 180, 248, 0.35);
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 12px;
  margin-top: 4px;
}
</style>

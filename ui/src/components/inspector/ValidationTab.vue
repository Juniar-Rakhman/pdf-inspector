<script setup lang="ts">
import { computed } from 'vue'
import { usePdfStore } from '../../stores/pdf'
import StatusBadge from '../StatusBadge.vue'
import { formatDate } from '../../lib/format'

const store = usePdfStore()

const validation = computed(() => store.validation)
</script>

<template>
  <div class="tab-body">
    <div v-if="store.isBusy('validation')" class="text-muted tab-hint">Validating…</div>

    <div v-else-if="validation">
      <div class="summary-box">
        <i class="pi pi-check-circle" />
        {{ validation.summary }}
      </div>

      <template v-if="validation.messages.length">
        <section class="dt-card">
          <div class="dt-card-header"><i class="pi pi-info-circle" /> Notes</div>
          <ul class="messages">
            <li v-for="(m, i) in validation.messages" :key="i">{{ m }}</li>
          </ul>
        </section>
      </template>

      <section class="dt-card">
        <div class="dt-card-header"><i class="pi pi-pen" /> Signature integrity</div>
        <div v-for="sig in validation.signatures" :key="sig.signatureIndex" class="report-item">
          <div class="report-head">
            <span class="report-title">{{ sig.signerName || `Signature ${sig.signatureIndex + 1}` }}</span>
            <span class="report-spacer" />
            <StatusBadge :status="sig.signatureIntegrity" />
          </div>
          <div class="report-sub">
            Document integrity: <StatusBadge :status="sig.documentIntegrity" />
            <span v-if="sig.modifiedAfterSigning" class="status-badge warn">modified after signing</span>
          </div>
          <ul class="messages">
            <li v-for="(m, i) in sig.messages" :key="i">{{ m }}</li>
          </ul>
        </div>
      </section>

      <section class="dt-card">
        <div class="dt-card-header"><i class="pi pi-id-card" /> Certificate validity</div>
        <div v-for="cert in validation.certificates" :key="cert.index" class="report-item">
          <div class="report-head">
            <span class="report-title">{{ cert.subject }}</span>
            <span class="report-spacer" />
            <StatusBadge :status="cert.status" />
          </div>
          <div class="report-sub text-muted">
            {{ formatDate(cert.validFrom) }} → {{ formatDate(cert.validUntil) }}
            <span class="text-faint">(checked {{ formatDate(cert.checkTime) }})</span>
          </div>
          <ul class="messages">
            <li v-for="(m, i) in cert.messages" :key="i">{{ m }}</li>
          </ul>
        </div>
      </section>

      <section class="dt-card">
        <div class="dt-card-header"><i class="pi pi-sitemap" /> Certificate chains</div>
        <div v-for="chain in validation.chains" :key="chain.index" class="report-item">
          <div class="report-head">
            <span class="report-title">Signature {{ chain.index + 1 }}</span>
            <span class="report-spacer" />
            <StatusBadge :status="chain.complete ? 'COMPLETE' : 'INCOMPLETE'" />
            <span class="text-muted">path: {{ chain.pathLength }}</span>
          </div>
          <ul class="messages">
            <li v-for="(m, i) in chain.messages" :key="i">{{ m }}</li>
          </ul>
        </div>
      </section>

      <section class="dt-card">
        <div class="dt-card-header"><i class="pi pi-shield" /> Trust</div>
        <div v-for="t in validation.trust" :key="t.index" class="report-item">
          <div class="report-head">
            <span class="report-title">Signature {{ t.index + 1 }}</span>
            <span class="report-spacer" />
            <StatusBadge :status="t.status" />
          </div>
          <ul class="messages">
            <li v-for="(m, i) in t.messages" :key="i">{{ m }}</li>
          </ul>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.tab-body {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: auto;
}

.tab-hint {
  padding: 12px;
}

.summary-box {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--dt-accent-bg);
  border: 1px solid rgba(138, 180, 248, 0.35);
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 12px;
}

.messages {
  list-style: none;
  padding: 4px 12px 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--dt-text-muted);
}

.messages li::before {
  content: '• ';
  color: var(--dt-text-faint);
}

.report-item {
  border-bottom: 1px solid var(--dt-border);
  padding: 8px 12px;
}

.report-item:last-child {
  border-bottom: none;
}

.report-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.report-title {
  font-weight: 600;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.report-spacer {
  flex: 1;
}

.report-sub {
  font-size: 11px;
  margin: 4px 0;
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
</style>

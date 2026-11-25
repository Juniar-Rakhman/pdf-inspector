<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { usePdfStore } from '../stores/pdf'

const store = usePdfStore()
const router = useRouter()

const dragOver = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
const error = ref<string | null>(null)
const busy = ref(false)

function openPicker() {
  fileInput.value?.click()
}

function onFilesSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) void submit(file)
  input.value = ''
}

function onDrop(event: DragEvent) {
  dragOver.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file) void submit(file)
}

async function submit(file: File) {
  if (busy.value) return
  if (!file.name.toLowerCase().endsWith('.pdf')) {
    error.value = `"${file.name}" does not look like a PDF file.`
    return
  }
  error.value = null
  busy.value = true
  try {
    const documentId = await store.analyze(file)
    void router.push(`/viewer/${documentId}`)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="home">
    <header class="home-header">
      <div class="brand">
        <i class="pi pi-file-check" style="color: var(--dt-accent); font-size: 22px" />
        <div>
          <h1>PDF Signature Inspector</h1>
          <p class="text-muted">
            Upload a digitally signed PDF to inspect its signatures, certificates, CMS structures and validation status.
          </p>
        </div>
      </div>
    </header>

    <main class="home-main">
      <div
        class="dropzone"
        :class="{ over: dragOver }"
        @click="openPicker"
        @dragenter.prevent="dragOver = true"
        @dragover.prevent="dragOver = true"
        @dragleave.prevent="dragOver = false"
        @drop.prevent="onDrop"
      >
        <i :class="busy ? 'pi pi-spin pi-spinner' : 'pi pi-cloud-upload'" class="dropzone-icon" />
        <div class="dropzone-title">{{ busy ? 'Analyzing…' : 'Drag & drop a signed PDF' }}</div>
        <div class="text-muted">or</div>
        <button class="dt-btn dt-btn-primary" :disabled="busy" @click.stop="openPicker">
          <i class="pi pi-folder-open" /> Browse PDF
        </button>
        <input ref="fileInput" type="file" accept="application/pdf,.pdf" hidden @change="onFilesSelected" />
      </div>

      <div v-if="error" class="error-panel">{{ error }}</div>

      <div class="feature-grid">
        <div class="feature">
          <i class="pi pi-pen" />
          <div>
            <h3>Signatures</h3>
            <p>Field names, pages, rectangles, signer names, byte ranges and claimed times.</p>
          </div>
        </div>
        <div class="feature">
          <i class="pi pi-sitemap" />
          <div>
            <h3>Certificate chains</h3>
            <p>Every embedded X.509 certificate with fingerprints, extensions and chain visualization.</p>
          </div>
        </div>
        <div class="feature">
          <i class="pi pi-file-o" />
          <div>
            <h3>CMS / PKCS#7</h3>
            <p>SignedData, SignerInfo, signed/unsigned attributes and raw ASN.1 trees.</p>
          </div>
        </div>
        <div class="feature">
          <i class="pi pi-check-circle" />
          <div>
            <h3>Validation</h3>
            <p>Signature integrity, document integrity, certificate validity, chain completeness and trust.</p>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.home {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.home-header {
  padding: 28px 40px 16px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 14px;
}

h1 {
  font-size: 22px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.home-main {
  flex: 1;
  max-width: 760px;
  margin: 0 auto;
  padding: 24px 40px;
  width: 100%;
}

.dropzone {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 48px 24px;
  border: 2px dashed var(--dt-border-strong);
  border-radius: 10px;
  background: var(--dt-bg-panel);
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.dropzone:hover,
.dropzone.over {
  border-color: var(--dt-accent);
  background: var(--dt-accent-bg);
}

.dropzone-icon {
  font-size: 42px;
  color: var(--dt-text-muted);
}

.dropzone-title {
  font-size: 16px;
  font-weight: 600;
}

.error-panel {
  margin-top: 12px;
  border: 1px solid rgba(242, 139, 130, 0.4);
}

.feature-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-top: 28px;
}

.feature {
  display: flex;
  gap: 12px;
  padding: 14px;
  background: var(--dt-bg-panel);
  border: 1px solid var(--dt-border);
  border-radius: 8px;
}

.feature i {
  color: var(--dt-accent);
  font-size: 18px;
  flex-shrink: 0;
  margin-top: 2px;
}

.feature h3 {
  font-size: 13px;
  margin-bottom: 4px;
}

.feature p {
  font-size: 12px;
  color: var(--dt-text-muted);
}
</style>

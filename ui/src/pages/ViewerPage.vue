<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import DevToolsToolbar from '../components/DevToolsToolbar.vue'
import PdfViewer from '../components/PdfViewer.vue'
import SignatureInspector from '../components/SignatureInspector.vue'
import { usePdfStore } from '../stores/pdf'

const props = defineProps<{
  documentId: string
}>()

const store = usePdfStore()

const splitHost = ref<HTMLDivElement | null>(null)
const leftWidth = ref(0)
const resizing = ref(false)

const MIN_LEFT = 320
const MIN_RIGHT = 380

function measure() {
  if (splitHost.value) {
    leftWidth.value = Math.round(splitHost.value.clientWidth * 0.52)
  }
}

function startResize(event: MouseEvent) {
  resizing.value = true
  document.body.classList.add('resizing')
  const startX = event.clientX
  const startWidth = leftWidth.value
  const move = (e: MouseEvent) => {
    if (!splitHost.value) return
    const delta = e.clientX - startX
    const max = splitHost.value.clientWidth - MIN_RIGHT
    leftWidth.value = Math.min(max, Math.max(MIN_LEFT, startWidth + delta))
  }
  const up = () => {
    resizing.value = false
    document.body.classList.remove('resizing')
    window.removeEventListener('mousemove', move)
    window.removeEventListener('mouseup', up)
  }
  window.addEventListener('mousemove', move)
  window.addEventListener('mouseup', up)
}

onMounted(() => {
  measure()
  window.addEventListener('resize', measure)
  if (store.documentId !== props.documentId) {
    void store.load(props.documentId)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', measure)
})

watch(
  () => props.documentId,
  (id) => {
    if (store.documentId !== id) void store.load(id)
  },
)
</script>

<template>
  <div class="viewer">
    <DevToolsToolbar />
    <div ref="splitHost" class="split" :class="{ resizing }">
      <div class="pane left" :style="{ width: leftWidth + 'px' }">
        <PdfViewer />
      </div>
      <div class="splitter" title="Drag to resize" @mousedown="startResize" />
      <div class="pane right">
        <SignatureInspector />
      </div>
    </div>
  </div>
</template>

<style scoped>
.viewer {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.split {
  flex: 1;
  min-height: 0;
  display: flex;
}

.split.resizing {
  user-select: none;
}

.pane {
  min-width: 0;
  overflow: hidden;
}

.pane.left {
  flex-shrink: 0;
}

.pane.right {
  flex: 1;
  min-width: 380px;
}

.splitter {
  width: 5px;
  cursor: col-resize;
  background: var(--dt-bg-panel);
  border-left: 1px solid var(--dt-border);
  border-right: 1px solid var(--dt-border);
  flex-shrink: 0;
  z-index: 5;
}

.splitter:hover,
.split.resizing .splitter {
  background: var(--dt-accent);
  opacity: 0.8;
}
</style>

<style>
body.resizing {
  cursor: col-resize !important;
}
</style>

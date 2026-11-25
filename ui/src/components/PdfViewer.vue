<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { getDocument } from 'pdfjs-dist'
import type { PDFDocumentProxy, PDFPageProxy } from 'pdfjs-dist'
import { contentUrl, revisionContentUrl } from '../api'
import { usePdfStore } from '../stores/pdf'

interface Overlay {
  x: number
  y: number
  width: number
  height: number
  signatureIndex: number | null
  fieldName: string
}

interface Rect {
  x: number
  y: number
  width: number
  height: number
}

interface SearchMatch {
  key: string
  page: number
  rect: Rect
}

type PageViewport = ReturnType<PDFPageProxy['getViewport']>

const store = usePdfStore()

const scrollEl = ref<HTMLDivElement | null>(null)
const pagesEl = ref<HTMLDivElement | null>(null)
const canvasRefs = new Map<number, HTMLCanvasElement>()
const thumbRefs = new Map<number, HTMLCanvasElement>()
const wrapRefs = new Map<number, HTMLDivElement>()

const pdfDoc = shallowRef<PDFDocumentProxy | null>(null)
const numPages = ref(0)
const loading = ref(false)
const loadError = ref<string | null>(null)
const currentPage = ref(1)

const fitMode = ref<'none' | 'width' | 'page'>('width')
const zoomPercent = ref(100)
const rotation = ref(0)
const showThumbnails = ref(true)
const showWidgets = ref(true)

const pageDims = ref<Record<number, { width: number; height: number }>>({})
const pageViewports = ref<Record<number, PageViewport>>({})
const widgetOverlays = ref<Record<number, Overlay[]>>({})
const thumbHeights = ref<Record<number, number>>({})

const searchQuery = ref('')
const searchResults = ref<SearchMatch[]>([])
const searchIndex = ref(0)
const currentSearchKey = computed(() => searchResults.value[searchIndex.value]?.key)
const searchMarks = computed(() => {
  const map: Record<number, { key: string; rect: Rect }[]> = {}
  for (const m of searchResults.value) {
    ;(map[m.page] ??= []).push({ key: m.key, rect: m.rect })
  }
  return map
})

let renderVersion = 0
let renderTasks = new Map<number, { cancel: () => void }>()
let resizeObserver: ResizeObserver | null = null

const sourceUrl = computed(() => {
  if (!store.documentId) return null
  if (store.activeRevisionIndex !== null) {
    return revisionContentUrl(store.documentId, store.activeRevisionIndex)
  }
  return contentUrl(store.documentId)
})

const pageNumbers = computed(() => Array.from({ length: numPages.value }, (_, i) => i + 1))

const thumbWidth = 110

function setCanvasRef(n: number, el: unknown) {
  if (el instanceof HTMLCanvasElement) canvasRefs.set(n, el)
  else canvasRefs.delete(n)
}

function setThumbRef(n: number, el: unknown) {
  if (el instanceof HTMLCanvasElement) thumbRefs.set(n, el)
  else thumbRefs.delete(n)
}

function setWrapRef(n: number, el: unknown) {
  if (el instanceof HTMLDivElement) wrapRefs.set(n, el)
  else wrapRefs.delete(n)
}

function wrapStyle(n: number) {
  const dims = pageDims.value[n]
  if (!dims) return {}
  return { width: `${dims.width}px`, height: `${dims.height}px` }
}

function overlayCss(rect: Rect) {
  return {
    left: `${rect.x}px`,
    top: `${rect.y}px`,
    width: `${rect.width}px`,
    height: `${rect.height}px`,
  }
}

function isCurrentSearchMark(n: number, i: number) {
  const marks = searchMarks.value[n]
  return marks?.[i]?.key === currentSearchKey.value
}

function availableWidth(): number {
  return Math.max((scrollEl.value?.clientWidth ?? 400) - 40, 200)
}

function availableHeight(): number {
  return Math.max((scrollEl.value?.clientHeight ?? 600) - 24, 200)
}

function resolveScale(): number {
  const doc = pdfDoc.value
  if (!doc || !pageDims.value[1]) return 1
  if (fitMode.value === 'width') {
    return availableWidth() / pageDims.value[1].width
  }
  if (fitMode.value === 'page') {
    const first = pageViewports.value[1]
    const h = first ? first.height : pageDims.value[1].height
    return availableHeight() / h
  }
  return zoomPercent.value / 100
}

async function loadDocument() {
  const url = sourceUrl.value
  if (!url) return
  cancelRenders()
  await pdfDoc.value?.destroy()
  pdfDoc.value = null
  numPages.value = 0
  pageDims.value = {}
  pageViewports.value = {}
  widgetOverlays.value = {}
  thumbHeights.value = {}
  searchResults.value = []
  currentPage.value = 1
  loading.value = true
  loadError.value = null
  try {
    const doc = await getDocument({ url }).promise
    pdfDoc.value = doc
    numPages.value = doc.numPages
    const dims: Record<number, { width: number; height: number }> = {}
    for (let i = 1; i <= doc.numPages; i++) {
      const page = await doc.getPage(i)
      const vp = page.getViewport({ scale: 1 })
      dims[i] = { width: vp.width, height: vp.height }
    }
    pageDims.value = dims
    await nextTick()
    await refresh()
    await renderThumbnails()
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

function cancelRenders() {
  for (const task of renderTasks.values()) {
    try {
      task.cancel()
    } catch {
      // task already finished
    }
  }
  renderTasks.clear()
}

async function refresh() {
  const doc = pdfDoc.value
  if (!doc) return
  cancelRenders()
  renderVersion++
  const version = renderVersion
  const scale = resolveScale()
  const vps: Record<number, PageViewport> = {}
  const dims: Record<number, { width: number; height: number }> = {}
  for (let i = 1; i <= doc.numPages; i++) {
    const page = await doc.getPage(i)
    const vp = page.getViewport({ scale, rotation: rotation.value })
    vps[i] = vp
    dims[i] = { width: vp.width, height: vp.height }
  }
  if (version !== renderVersion) return
  pageViewports.value = vps
  pageDims.value = dims
  await nextTick()
  if (version !== renderVersion) return
  for (let i = 1; i <= doc.numPages; i++) {
    const canvas = canvasRefs.get(i)
    const page = await doc.getPage(i)
    const vp = vps[i]
    if (canvas && vp) {
      const task = page.render({
        canvasContext: canvas.getContext('2d') as CanvasRenderingContext2D,
        viewport: vp,
        background: '#ffffff',
      })
      renderTasks.set(i, task)
    }
  }
  await Promise.all(Array.from(renderTasks.values()))
  renderTasks.clear()
  computeOverlays()
  if (searchQuery.value.trim()) {
    void runSearch(true)
  }
}

function computeOverlays() {
  const result: Record<number, Overlay[]> = {}
  for (const widget of store.widgets) {
    if (!widget.rectangle) continue
    const vp = pageViewports.value[widget.page]
    if (!vp) continue
    const rect = viewportRect(vp, widget.rectangle.x, widget.rectangle.y, widget.rectangle.width, widget.rectangle.height)
    ;(result[widget.page] ??= []).push({
      ...rect,
      signatureIndex: widget.signatureIndex,
      fieldName: widget.fieldName,
    })
  }
  widgetOverlays.value = result
}

function viewportRect(vp: PageViewport, x: number, y: number, width: number, height: number): Rect {
  const p1 = vp.convertToViewportPoint(x, y)
  const p2 = vp.convertToViewportPoint(x + width, y + height)
  return {
    x: Math.min(p1[0], p2[0]),
    y: Math.min(p1[1], p2[1]),
    width: Math.abs(p2[0] - p1[0]),
    height: Math.abs(p2[1] - p1[1]),
  }
}

async function renderThumbnails() {
  const doc = pdfDoc.value
  if (!doc) return
  const heights: Record<number, number> = {}
  for (let i = 1; i <= doc.numPages; i++) {
    const page = await doc.getPage(i)
    const base = page.getViewport({ scale: 1 })
    const scale = thumbWidth / base.width
    const vp = page.getViewport({ scale })
    heights[i] = vp.height
    const canvas = thumbRefs.get(i)
    if (canvas) {
      canvas.width = vp.width
      canvas.height = vp.height
      void page.render({
        canvasContext: canvas.getContext('2d') as CanvasRenderingContext2D,
        viewport: vp,
        background: '#ffffff',
      })
    }
  }
  thumbHeights.value = heights
}

function onWidgetClick(overlay: Overlay) {
  if (overlay.signatureIndex === null) return
  void store.selectSignature(overlay.signatureIndex)
  scrollToPage(store.selectedSignatureIndex !== null ? selectedWidgetPage(overlay) : 1)
}

function selectedWidgetPage(overlay: Overlay): number {
  const widget = store.widgets.find((w) => w.signatureIndex === overlay.signatureIndex)
  return widget?.page ?? currentPage.value
}

function scrollToPage(page: number) {
  const wrap = wrapRefs.get(page)
  if (!wrap || !scrollEl.value) return
  scrollEl.value.scrollTo({ top: wrap.offsetTop - 8, behavior: 'smooth' })
  currentPage.value = page
}

function onScroll() {
  if (!scrollEl.value) return
  const scrollTop = scrollEl.value.scrollTop + 60
  let found = 1
  for (const [n, wrap] of wrapRefs) {
    if (wrap.offsetTop <= scrollTop) found = n
  }
  if (currentPage.value !== found) currentPage.value = found
}

function zoomBy(factor: number) {
  fitMode.value = 'none'
  zoomPercent.value = Math.min(500, Math.max(20, Math.round(zoomPercent.value * factor)))
}

function setFit(mode: 'none' | 'width' | 'page') {
  fitMode.value = mode
  if (mode === 'none') zoomPercent.value = 100
}

function rotate(dir: -90 | 90) {
  rotation.value = (rotation.value + dir + 360) % 360
}

async function runSearch(keepIndex = false) {
  const query = searchQuery.value.trim().toLowerCase()
  if (!query || !pdfDoc.value) {
    searchResults.value = []
    searchIndex.value = 0
    return
  }
  const results: SearchMatch[] = []
  for (let p = 1; p <= pdfDoc.value.numPages; p++) {
    const page = await pdfDoc.value.getPage(p)
    const content = await page.getTextContent()
    const vp = pageViewports.value[p]
    if (!vp) continue
    for (const item of content.items) {
      if (!('str' in item) || !item.str) continue
      const str = item.str
      const lower = str.toLowerCase()
      const charW = item.width / Math.max(str.length, 1)
      const textHeight = item.height * vp.scale
      let start = lower.indexOf(query)
      while (start !== -1) {
        const [bx] = vp.convertToViewportPoint(
          item.transform[4] + charW * start,
          item.transform[5],
        )
        const [bx2] = vp.convertToViewportPoint(
          item.transform[4] + charW * (start + query.length),
          item.transform[5],
        )
        const [, by] = vp.convertToViewportPoint(item.transform[4], item.transform[5])
        results.push({
          key: `${p}:${start}:${item.str.length}:${bx}`,
          page: p,
          rect: { x: bx, y: by - textHeight, width: Math.max(bx2 - bx, 2), height: textHeight },
        })
        start = lower.indexOf(query, start + query.length)
      }
    }
  }
  searchResults.value = results
  if (!keepIndex) {
    searchIndex.value = 0
    if (results.length) jumpToSearch(0)
  }
}

function jumpToSearch(i: number) {
  const match = searchResults.value[i]
  if (!match) return
  searchIndex.value = i
  scrollToPage(match.page)
}

function searchStep(dir: 1 | -1) {
  const total = searchResults.value.length
  if (!total) return
  searchIndex.value = (searchIndex.value + dir + total) % total
  jumpToSearch(searchIndex.value)
}

watch(sourceUrl, () => {
  void loadDocument()
})

watch([fitMode, zoomPercent, rotation], () => {
  void refresh()
})

watch(showThumbnails, () => {
  nextTick(() => void refresh())
})

watch(
  () => store.navigateRequest,
  (req) => {
    if (req) scrollToPage(req.page)
  },
)

watch(
  () => store.widgets,
  () => computeOverlays(),
  { deep: true },
)

watch(
  () => store.selectedSignatureIndex,
  () => computeOverlays(),
)

onMounted(() => {
  resizeObserver = new ResizeObserver(() => {
    if (fitMode.value === 'width' || fitMode.value === 'page') {
      void refresh()
    }
  })
  if (scrollEl.value) resizeObserver.observe(scrollEl.value)
  if (store.documentId && !pdfDoc.value) void loadDocument()
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  cancelRenders()
  void pdfDoc.value?.destroy()
})
</script>

<template>
  <div class="pdf-viewer">
    <div class="pdf-toolbar">
      <button class="dt-btn icon-only" :class="{ active: showThumbnails }" title="Toggle thumbnails" @click="showThumbnails = !showThumbnails">
        <i class="pi pi-th-large" />
      </button>

      <button class="dt-btn icon-only" title="Previous page" :disabled="currentPage <= 1" @click="scrollToPage(currentPage - 1)">
        <i class="pi pi-chevron-left" />
      </button>
      <input
        class="dt-input page-input"
        type="number"
        :value="currentPage"
        min="1"
        :max="numPages"
        @change="(e: Event) => scrollToPage(Math.min(numPages, Math.max(1, Number((e.target as HTMLInputElement).value))))"
      />
      <span class="text-muted page-total">/ {{ numPages }}</span>
      <button class="dt-btn icon-only" title="Next page" :disabled="currentPage >= numPages" @click="scrollToPage(currentPage + 1)">
        <i class="pi pi-chevron-right" />
      </button>

      <span class="toolbar-divider" />

      <button class="dt-btn icon-only" title="Zoom out" @click="zoomBy(0.8)">
        <i class="pi pi-minus" />
      </button>
      <span class="zoom-label">{{ Math.round(zoomPercent) }}%</span>
      <button class="dt-btn icon-only" title="Zoom in" @click="zoomBy(1.25)">
        <i class="pi pi-plus" />
      </button>
      <button class="dt-btn" :class="{ active: fitMode === 'width' }" title="Fit width" @click="setFit('width')">
        <i class="pi pi-arrows-h" />
      </button>
      <button class="dt-btn" :class="{ active: fitMode === 'page' }" title="Fit page" @click="setFit('page')">
        <i class="pi pi-arrows-v" />
      </button>

      <span class="toolbar-divider" />

      <button class="dt-btn icon-only" title="Rotate left" @click="rotate(-90)">↺</button>
      <button class="dt-btn icon-only" title="Rotate right" @click="rotate(90)">↻</button>

      <span class="toolbar-divider" />

      <button class="dt-btn" :class="{ active: showWidgets }" title="Toggle signature widgets" @click="showWidgets = !showWidgets">
        <i class="pi pi-pen" />
      </button>

      <span class="toolbar-spacer" />

      <input
        v-model="searchQuery"
        class="dt-input search-input"
        type="search"
        placeholder="Search document…"
        @keydown.enter="runSearch()"
      />
      <button class="dt-btn icon-only" title="Search" @click="runSearch()">
        <i class="pi pi-search" />
      </button>
      <button class="dt-btn icon-only" title="Previous match" :disabled="searchResults.length === 0" @click="searchStep(-1)">
        <i class="pi pi-angle-up" />
      </button>
      <button class="dt-btn icon-only" title="Next match" :disabled="searchResults.length === 0" @click="searchStep(1)">
        <i class="pi pi-angle-down" />
      </button>
      <span v-if="searchResults.length" class="text-muted search-count">
        {{ searchIndex + 1 }}/{{ searchResults.length }}
      </span>

      <span v-if="loading" class="spinner" title="Loading PDF" />
    </div>

    <div class="pdf-body">
      <div v-if="showThumbnails" class="pdf-thumbs">
        <div
          v-for="n in pageNumbers"
          :key="'t' + n"
          class="pdf-thumb"
          :class="{ active: currentPage === n }"
          @click="scrollToPage(n)"
        >
          <canvas :ref="(el: unknown) => setThumbRef(n, el)" :style="{ width: thumbWidth + 'px', height: (thumbHeights[n] ?? 80) + 'px' }" />
          <span class="pdf-thumb-num">{{ n }}</span>
        </div>
      </div>

      <div ref="scrollEl" class="pdf-scroll" @scroll.passive="onScroll">
        <div ref="pagesEl" class="pdf-pages">
          <div
            v-for="n in pageNumbers"
            :key="n"
            class="pdf-page-wrap"
            :ref="(el: unknown) => setWrapRef(n, el)"
            :style="wrapStyle(n)"
          >
            <canvas :ref="(el: unknown) => setCanvasRef(n, el)" />
            <template v-if="showWidgets">
              <div
                v-for="(ov, i) in widgetOverlays[n] ?? []"
                :key="'w' + i"
                class="widget-overlay"
                :class="{ selected: ov.signatureIndex === store.selectedSignatureIndex }"
                :style="overlayCss(ov)"
                :title="ov.fieldName"
                @click.stop="onWidgetClick(ov)"
              />
            </template>
            <div
              v-for="(m, i) in searchMarks[n] ?? []"
              :key="'s' + i"
              class="search-mark"
              :class="{ current: isCurrentSearchMark(n, i) }"
              :style="overlayCss(m.rect)"
            />
          </div>
          <div v-if="loading" class="pdf-loading">
            <span class="spinner" />
            Loading document…
          </div>
          <div v-if="loadError" class="error-panel">
            Failed to load PDF: {{ loadError }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pdf-viewer {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 0;
  background: var(--dt-bg);
}

.pdf-toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 34px;
  padding: 0 8px;
  background: var(--dt-bg-panel);
  border-bottom: 1px solid var(--dt-border);
  flex-shrink: 0;
  user-select: none;
}

.toolbar-divider {
  width: 1px;
  height: 18px;
  background: var(--dt-border-strong);
  margin: 0 4px;
}

.icon-only {
  padding: 0 7px;
}

.page-input {
  width: 48px;
  text-align: center;
}

.page-total {
  font-size: 12px;
}

.zoom-label {
  min-width: 44px;
  text-align: center;
  font-size: 12px;
  color: var(--dt-text);
  user-select: none;
}

.search-input {
  width: 160px;
}

.search-count {
  font-size: 11px;
  white-space: nowrap;
}

.pdf-body {
  display: flex;
  flex: 1;
  min-height: 0;
}

.pdf-thumbs {
  width: 138px;
  overflow-y: auto;
  padding: 8px;
  background: var(--dt-bg-panel);
  border-right: 1px solid var(--dt-border);
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.pdf-thumb {
  position: relative;
  cursor: pointer;
  border: 1px solid transparent;
  border-radius: 3px;
  padding: 3px;
}

.pdf-thumb.active {
  border-color: var(--dt-accent);
  background: var(--dt-accent-bg);
}

.pdf-thumb canvas {
  display: block;
}

.pdf-thumb-num {
  position: absolute;
  top: 6px;
  right: 6px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 10px;
  padding: 0 5px;
  border-radius: 8px;
}

.pdf-scroll {
  flex: 1;
  overflow: auto;
  padding: 8px 0;
}

.pdf-pages {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding-bottom: 24px;
  min-height: 100%;
}

.pdf-page-wrap {
  position: relative;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.5);
  background: #fff;
  flex-shrink: 0;
}

.pdf-page-wrap canvas {
  display: block;
  width: 100%;
  height: 100%;
}

.widget-overlay {
  position: absolute;
  border: 2px solid var(--dt-accent);
  background: rgba(138, 180, 248, 0.18);
  cursor: pointer;
  box-sizing: border-box;
}

.widget-overlay:hover {
  background: rgba(138, 180, 248, 0.35);
}

.widget-overlay.selected {
  border-color: var(--dt-orange);
  background: rgba(248, 162, 74, 0.3);
  box-shadow: 0 0 0 2px rgba(248, 162, 74, 0.4);
}

.search-mark {
  position: absolute;
  background: rgba(253, 214, 99, 0.35);
  box-shadow: inset 0 0 0 1px rgba(253, 214, 99, 0.6);
}

.search-mark.current {
  background: rgba(248, 162, 74, 0.6);
  box-shadow: inset 0 0 0 2px var(--dt-orange);
}

.pdf-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--dt-text-muted);
  padding: 24px;
}

.spinner {
  width: 14px;
  height: 14px;
  border: 2px solid var(--dt-border-strong);
  border-top-color: var(--dt-accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  display: inline-block;
  flex-shrink: 0;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>

<script setup lang="ts">
import { computed } from 'vue'
import { hexLines, groupHex } from '../lib/format'

const props = withDefaults(
  defineProps<{
    hex: string
    maxBytes?: number
    copyable?: boolean
  }>(),
  {
    maxBytes: 64,
    copyable: true,
  },
)

const cleaned = computed(() => props.hex.replace(/\s+/g, ''))
const truncated = computed(() => cleaned.value.length > props.maxBytes * 2)
const lines = computed(() => hexLines(cleaned.value, props.maxBytes > 32 ? 32 : 16))

function copy() {
  void navigator.clipboard.writeText(groupHex(cleaned.value))
}
</script>

<template>
  <div class="hex-value">
    <div v-if="copyable" class="hex-toolbar">
      <button class="dt-btn" title="Copy hex" @click="copy">
        <i class="pi pi-copy" />
      </button>
    </div>
    <pre class="hex-lines"><code v-for="(line, i) in lines" :key="i">{{ line }}</code></pre>
    <div v-if="truncated" class="text-faint">… truncated ({{ Math.ceil(cleaned.length / 2) }} bytes total)</div>
  </div>
</template>

<style scoped>
.hex-value {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.hex-toolbar {
  flex-shrink: 0;
}

.hex-lines {
  display: flex;
  flex-direction: column;
  gap: 1px;
  overflow-x: auto;
}

.hex-lines code {
  background: transparent;
  padding: 0;
  white-space: pre;
}
</style>

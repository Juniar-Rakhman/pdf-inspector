<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  status: string
}>()

const className = computed(() => {
  switch (props.status) {
    case 'VALID':
    case 'TRUSTED':
    case 'TRUE':
    case 'COMPLETE':
      return 'ok'
    case 'INVALID':
    case 'EXPIRED':
    case 'FALSE':
    case 'INCOMPLETE':
    case 'FAILED':
      return 'bad'
    case 'NOT_YET_VALID':
    case 'MODIFIED':
      return 'warn'
    default:
      return 'unknown'
  }
})
</script>

<template>
  <span class="status-badge" :class="className">{{ status }}</span>
</template>

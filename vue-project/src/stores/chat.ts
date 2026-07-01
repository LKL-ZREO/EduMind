import { ref } from 'vue'
import { defineStore } from 'pinia'

export const useChatStore = defineStore('chat', () => {
  const aiResponding = ref(false)
  const partialContent = ref('')

  function setResponding(v: boolean) {
    aiResponding.value = v
  }

  function setPartial(content: string) {
    partialContent.value = content
  }

  function clearPartial() {
    partialContent.value = ''
  }

  return { aiResponding, partialContent, setResponding, setPartial, clearPartial }
})

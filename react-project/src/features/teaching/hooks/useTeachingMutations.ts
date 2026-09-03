import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  addCalendarPlan,
  addTeacherKnowledge,
  closePreviewTask,
  createPreviewTask,
  generateTeachingPlan,
  getPreLessonSuggestion,
  saveTeacherKnowledge,
} from '@/features/teaching/api/teachingApi'
import { teachingKeys } from '@/features/teaching/api/teachingQueries'
import type { CalendarPlan, TeacherKnowledgeItem } from '@/features/teaching/model/types'

export function useTeachingMutations() {
  const queryClient = useQueryClient()
  return {
    addKnowledge: useMutation({
      mutationFn: ({ classId, name, color }: { classId: number; name: string; color: string }) =>
        addTeacherKnowledge(classId, name, color),
      onSuccess: (_, variables) =>
        queryClient.invalidateQueries({ queryKey: teachingKeys.knowledge(variables.classId) }),
    }),
    saveKnowledge: useMutation({
      mutationFn: ({ classId, items }: { classId: number; items: TeacherKnowledgeItem[] }) =>
        saveTeacherKnowledge(classId, items),
      onSuccess: (_, variables) =>
        queryClient.invalidateQueries({ queryKey: teachingKeys.knowledge(variables.classId) }),
    }),
    generatePlan: useMutation({ mutationFn: generateTeachingPlan }),
    getSuggestion: useMutation({
      mutationFn: (classId: number) => getPreLessonSuggestion(classId),
    }),
    addCalendar: useMutation({
      mutationFn: (plan: CalendarPlan) => addCalendarPlan(plan),
      onSuccess: (_, plan) =>
        queryClient.invalidateQueries({ queryKey: teachingKeys.timeline(plan.classId) }),
    }),
    createPreview: useMutation({
      mutationFn: createPreviewTask,
      onSuccess: (_, payload) =>
        queryClient.invalidateQueries({ queryKey: teachingKeys.previews(payload.classId) }),
    }),
    closePreview: useMutation({
      mutationFn: ({ taskId }: { taskId: number; classId: number }) => closePreviewTask(taskId),
      onSuccess: (_, payload) =>
        queryClient.invalidateQueries({ queryKey: teachingKeys.previews(payload.classId) }),
    }),
  }
}

import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  bindStudentQq,
  deleteHomeworkDraft,
  publishHomeworkDraft,
  saveHomeworkDraft,
  submitHomework,
} from '@/features/homework/api/homeworkApi'
import { homeworkKeys } from '@/features/homework/api/homeworkQueries'
import type { PublishDraftPayload, SaveDraftPayload } from '@/features/homework/model/types'

export function useTeacherHomeworkMutations() {
  const queryClient = useQueryClient()
  return {
    saveDraft: useMutation({
      mutationFn: ({ payload, draftId }: { payload: SaveDraftPayload; draftId?: number | null }) =>
        saveHomeworkDraft(payload, draftId),
      onSuccess: () => {
        void queryClient.invalidateQueries({ queryKey: homeworkKeys.drafts() })
        void queryClient.invalidateQueries({ queryKey: homeworkKeys.questions() })
      },
    }),
    deleteDraft: useMutation({
      mutationFn: deleteHomeworkDraft,
      onSuccess: () => queryClient.invalidateQueries({ queryKey: homeworkKeys.drafts() }),
    }),
    publishDraft: useMutation({
      mutationFn: ({ draftId, payload }: { draftId: number; payload: PublishDraftPayload }) =>
        publishHomeworkDraft(draftId, payload),
      onSuccess: () => {
        void queryClient.invalidateQueries({ queryKey: homeworkKeys.drafts() })
        void queryClient.invalidateQueries({ queryKey: homeworkKeys.teacherTasks() })
      },
    }),
  }
}

export function useStudentHomeworkMutations() {
  return {
    submit: useMutation({ mutationFn: submitHomework }),
    bindQq: useMutation({ mutationFn: bindStudentQq }),
  }
}

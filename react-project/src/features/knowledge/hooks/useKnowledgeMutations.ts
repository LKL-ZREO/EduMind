import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  createFolder,
  createKnowledgeBase,
  deleteKnowledgeBase,
  deleteMaterial,
  deleteNode,
  generateInvite,
  generateMaterials,
  joinKnowledgeBase,
  moveNode,
  removeKnowledgeBaseMember,
  renameNode,
  saveGeneratedPreview,
  saveGeneratedQuestion,
  updateKnowledgeBase,
  uploadDocuments,
} from '@/features/knowledge/api/knowledgeApi'
import { knowledgeKeys } from '@/features/knowledge/api/knowledgeQueries'
import type {
  FolderPayload,
  GeneratedQuestion,
  GenerateMaterialsPayload,
  SavePreviewPayload,
  UploadDocumentsPayload,
} from '@/features/knowledge/model/types'

export function useKnowledgeMutations() {
  const queryClient = useQueryClient()
  const invalidateTree = (kbId: number | null) =>
    queryClient.invalidateQueries({ queryKey: knowledgeKeys.tree(kbId) })

  return {
    createFolder: useMutation({
      mutationFn: (payload: FolderPayload) => createFolder(payload),
      onSuccess: (_, payload) => invalidateTree(payload.kbId),
    }),
    renameNode: useMutation({
      mutationFn: ({ nodeId, label }: { nodeId: number; label: string; kbId: number | null }) =>
        renameNode(nodeId, label),
      onSuccess: (_, variables) => invalidateTree(variables.kbId),
    }),
    deleteNode: useMutation({
      mutationFn: ({ nodeId }: { nodeId: number; kbId: number | null }) => deleteNode(nodeId),
      onSuccess: (_, variables) => invalidateTree(variables.kbId),
    }),
    moveNode: useMutation({
      mutationFn: ({
        nodeId,
        targetParentId,
      }: {
        nodeId: number
        targetParentId: number | null
        kbId: number | null
      }) => moveNode(nodeId, targetParentId),
      onSuccess: (_, variables) => invalidateTree(variables.kbId),
    }),
    uploadDocuments: useMutation({
      mutationFn: (payload: UploadDocumentsPayload) => uploadDocuments(payload),
      onSuccess: (_, payload) => invalidateTree(payload.kbId),
    }),
    createKnowledgeBase: useMutation({
      mutationFn: createKnowledgeBase,
      onSuccess: () => queryClient.invalidateQueries({ queryKey: knowledgeKeys.spaces() }),
    }),
    updateKnowledgeBase: useMutation({
      mutationFn: ({
        kbId,
        name,
        description,
      }: {
        kbId: number
        name: string
        description: string
      }) => updateKnowledgeBase(kbId, { name, description }),
      onSuccess: () => queryClient.invalidateQueries({ queryKey: knowledgeKeys.spaces() }),
    }),
    deleteKnowledgeBase: useMutation({
      mutationFn: deleteKnowledgeBase,
      onSuccess: (_, kbId) => {
        queryClient.removeQueries({ queryKey: knowledgeKeys.tree(kbId) })
        return queryClient.invalidateQueries({ queryKey: knowledgeKeys.spaces() })
      },
    }),
    joinKnowledgeBase: useMutation({
      mutationFn: joinKnowledgeBase,
      onSuccess: () => queryClient.invalidateQueries({ queryKey: knowledgeKeys.spaces() }),
    }),
    generateInvite: useMutation({ mutationFn: generateInvite }),
    removeMember: useMutation({
      mutationFn: ({ kbId, userId }: { kbId: number; userId: number }) =>
        removeKnowledgeBaseMember(kbId, userId),
      onSuccess: (_, variables) =>
        queryClient.invalidateQueries({ queryKey: knowledgeKeys.kbMembers(variables.kbId) }),
    }),
    generateMaterials: useMutation({
      mutationFn: (payload: GenerateMaterialsPayload) => generateMaterials(payload),
    }),
    savePreview: useMutation({
      mutationFn: (payload: SavePreviewPayload) => saveGeneratedPreview(payload),
      onSuccess: (_, payload) =>
        queryClient.invalidateQueries({
          queryKey: knowledgeKeys.documentMaterials(payload.docId),
        }),
    }),
    saveQuestion: useMutation({
      mutationFn: ({ question, docId }: { question: GeneratedQuestion; docId: string }) =>
        saveGeneratedQuestion(question, docId),
      onSuccess: (_, payload) =>
        queryClient.invalidateQueries({
          queryKey: knowledgeKeys.documentMaterials(payload.docId),
        }),
    }),
    deleteMaterial: useMutation({
      mutationFn: ({ type, id }: { type: 'preview' | 'quiz'; id: number; docId: string }) =>
        deleteMaterial(type, id),
      onSuccess: (_, payload) =>
        queryClient.invalidateQueries({
          queryKey: knowledgeKeys.documentMaterials(payload.docId),
        }),
    }),
  }
}

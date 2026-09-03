import { queryOptions } from '@tanstack/react-query'
import {
  getDirectoryTree,
  getDocumentContent,
  getDocumentMaterials,
  getMaterialDetail,
  getKnowledgeBaseMembers,
  getKnowledgeClasses,
  getKnowledgeSpaces,
} from '@/features/knowledge/api/knowledgeApi'

export const knowledgeKeys = {
  all: ['knowledge'] as const,
  spaces: () => [...knowledgeKeys.all, 'spaces'] as const,
  trees: () => [...knowledgeKeys.all, 'tree'] as const,
  tree: (kbId: number | null) => [...knowledgeKeys.trees(), kbId ?? 'personal'] as const,
  contents: () => [...knowledgeKeys.all, 'content'] as const,
  content: (docId: string) => [...knowledgeKeys.contents(), docId] as const,
  materials: () => [...knowledgeKeys.all, 'materials'] as const,
  documentMaterials: (docId: string) => [...knowledgeKeys.materials(), docId] as const,
  materialDetail: (type: 'preview' | 'quiz', id: number) =>
    [...knowledgeKeys.materials(), 'detail', type, id] as const,
  members: () => [...knowledgeKeys.all, 'members'] as const,
  kbMembers: (kbId: number) => [...knowledgeKeys.members(), kbId] as const,
  classes: () => [...knowledgeKeys.all, 'classes'] as const,
}

export const knowledgeSpacesQueryOptions = () =>
  queryOptions({
    queryKey: knowledgeKeys.spaces(),
    queryFn: getKnowledgeSpaces,
    staleTime: 60_000,
  })

export const directoryTreeQueryOptions = (kbId: number | null) =>
  queryOptions({
    queryKey: knowledgeKeys.tree(kbId),
    queryFn: () => getDirectoryTree(kbId),
    staleTime: 30_000,
  })

export const documentContentQueryOptions = (docId: string) =>
  queryOptions({
    queryKey: knowledgeKeys.content(docId),
    queryFn: () => getDocumentContent(docId),
    staleTime: Number.POSITIVE_INFINITY,
  })

export const documentMaterialsQueryOptions = (docId: string) =>
  queryOptions({
    queryKey: knowledgeKeys.documentMaterials(docId),
    queryFn: () => getDocumentMaterials(docId),
    staleTime: 30_000,
  })

export const materialDetailQueryOptions = (type: 'preview' | 'quiz', id: number) =>
  queryOptions({
    queryKey: knowledgeKeys.materialDetail(type, id),
    queryFn: () => getMaterialDetail(type, id),
    staleTime: 30_000,
  })

export const knowledgeBaseMembersQueryOptions = (kbId: number) =>
  queryOptions({
    queryKey: knowledgeKeys.kbMembers(kbId),
    queryFn: () => getKnowledgeBaseMembers(kbId),
    staleTime: 30_000,
  })

export const knowledgeClassesQueryOptions = () =>
  queryOptions({
    queryKey: knowledgeKeys.classes(),
    queryFn: getKnowledgeClasses,
    staleTime: 5 * 60_000,
  })

import { queryOptions } from '@tanstack/react-query'
import {
  getGradingResult,
  getHomeworkDrafts,
  getPublicClasses,
  getPublicTasks,
  getSubmissionContent,
  getTaskDetail,
  getTasksForClasses,
  searchHomeworkQuestions,
} from './homeworkApi'

export const homeworkKeys = {
  all: ['homework'] as const,
  teacherTasks: () => [...homeworkKeys.all, 'teacher-tasks'] as const,
  tasksForClasses: (classIds: number[]) =>
    [...homeworkKeys.teacherTasks(), [...classIds].sort((a, b) => a - b)] as const,
  drafts: () => [...homeworkKeys.all, 'drafts'] as const,
  questions: () => [...homeworkKeys.all, 'question-bank'] as const,
  questionSearch: (keyword: string) => [...homeworkKeys.questions(), keyword] as const,
  taskDetails: () => [...homeworkKeys.all, 'task-detail'] as const,
  taskDetail: (taskId: number) => [...homeworkKeys.taskDetails(), taskId] as const,
  publicClasses: () => [...homeworkKeys.all, 'public-classes'] as const,
  publicTasks: (classId: number) => [...homeworkKeys.all, 'public-tasks', classId] as const,
  grading: (submissionId: number) => [...homeworkKeys.all, 'grading', submissionId] as const,
  submissionContent: (submissionId: number) =>
    [...homeworkKeys.all, 'submission-content', submissionId] as const,
}

export const teacherTasksQueryOptions = (classIds: number[]) =>
  queryOptions({
    queryKey: homeworkKeys.tasksForClasses(classIds),
    queryFn: () => getTasksForClasses(classIds),
    enabled: classIds.length > 0,
    staleTime: 30_000,
  })

export const homeworkDraftsQueryOptions = () =>
  queryOptions({
    queryKey: homeworkKeys.drafts(),
    queryFn: getHomeworkDrafts,
    staleTime: 30_000,
  })

export const homeworkQuestionBankQueryOptions = (keyword: string) =>
  queryOptions({
    queryKey: homeworkKeys.questionSearch(keyword),
    queryFn: () => searchHomeworkQuestions(keyword),
    staleTime: 30_000,
  })

export const homeworkTaskDetailQueryOptions = (taskId: number) =>
  queryOptions({
    queryKey: homeworkKeys.taskDetail(taskId),
    queryFn: () => getTaskDetail(taskId),
    staleTime: 15_000,
  })

export const publicClassesQueryOptions = () =>
  queryOptions({
    queryKey: homeworkKeys.publicClasses(),
    queryFn: getPublicClasses,
    staleTime: 5 * 60_000,
  })

export const publicTasksQueryOptions = (classId: number) =>
  queryOptions({
    queryKey: homeworkKeys.publicTasks(classId),
    queryFn: () => getPublicTasks(classId),
    enabled: classId > 0,
    staleTime: 60_000,
  })

export const gradingResultQueryOptions = (submissionId: number) =>
  queryOptions({
    queryKey: homeworkKeys.grading(submissionId),
    queryFn: () => getGradingResult(submissionId),
    enabled: submissionId > 0,
  })

export const submissionContentQueryOptions = (submissionId: number) =>
  queryOptions({
    queryKey: homeworkKeys.submissionContent(submissionId),
    queryFn: () => getSubmissionContent(submissionId),
    enabled: submissionId > 0,
    staleTime: Number.POSITIVE_INFINITY,
  })

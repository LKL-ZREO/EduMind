import { queryOptions } from '@tanstack/react-query'
import {
  getConfusionSignals,
  getDashboardClasses,
  getDashboardMetrics,
  getFrequentErrors,
  getKnowledgeMastery,
  getPreLessonOverview,
  getPreviewTask,
  getReclassificationTask,
  getScoreDistribution,
  getStudentInsight,
  getStudentOverview,
  getTimeline,
  listPreviewTasks,
} from './teachingApi'
import type { StudentOverview } from '@/features/teaching/model/types'

export const teachingKeys = {
  all: ['teaching'] as const,
  classes: () => [...teachingKeys.all, 'classes'] as const,
  dashboard: (classId: number) => [...teachingKeys.all, 'dashboard', classId] as const,
  metrics: (classId: number) => [...teachingKeys.dashboard(classId), 'metrics'] as const,
  distribution: (classId: number) => [...teachingKeys.dashboard(classId), 'distribution'] as const,
  knowledge: (classId: number) => [...teachingKeys.dashboard(classId), 'knowledge'] as const,
  errors: (classId: number, point = '') =>
    [...teachingKeys.dashboard(classId), 'errors', point] as const,
  students: (classId: number) => [...teachingKeys.dashboard(classId), 'students'] as const,
  studentInsight: (classId: number, studentKey: string) =>
    [...teachingKeys.dashboard(classId), 'student-insight', studentKey] as const,
  confusions: (classId: number) => [...teachingKeys.dashboard(classId), 'confusions'] as const,
  reclassification: (classId: number, taskId: string) =>
    [...teachingKeys.dashboard(classId), 'reclassification', taskId] as const,
  preLesson: (classId: number) => [...teachingKeys.all, 'pre-lesson', classId] as const,
  timeline: (classId: number) => [...teachingKeys.all, 'timeline', classId] as const,
  previews: (classId: number) => [...teachingKeys.all, 'previews', classId] as const,
  preview: (taskId: number) => [...teachingKeys.all, 'preview', taskId] as const,
}

export const teachingClassesQueryOptions = () =>
  queryOptions({
    queryKey: teachingKeys.classes(),
    queryFn: getDashboardClasses,
    staleTime: 60_000,
  })

export const dashboardMetricsQueryOptions = (classId: number) =>
  queryOptions({
    queryKey: teachingKeys.metrics(classId),
    queryFn: () => getDashboardMetrics(classId),
    enabled: classId > 0,
    staleTime: 30_000,
  })

export const scoreDistributionQueryOptions = (classId: number) =>
  queryOptions({
    queryKey: teachingKeys.distribution(classId),
    queryFn: () => getScoreDistribution(classId),
    enabled: classId > 0,
    staleTime: 30_000,
  })

export const knowledgeMasteryQueryOptions = (classId: number) =>
  queryOptions({
    queryKey: teachingKeys.knowledge(classId),
    queryFn: () => getKnowledgeMastery(classId),
    enabled: classId > 0,
    staleTime: 30_000,
  })

export const frequentErrorsQueryOptions = (classId: number, knowledgePoint = '') =>
  queryOptions({
    queryKey: teachingKeys.errors(classId, knowledgePoint),
    queryFn: () => getFrequentErrors(classId, knowledgePoint || undefined),
    enabled: classId > 0,
    staleTime: 30_000,
  })

export const studentOverviewQueryOptions = (classId: number) =>
  queryOptions({
    queryKey: teachingKeys.students(classId),
    queryFn: () => getStudentOverview(classId),
    enabled: classId > 0,
    staleTime: 30_000,
  })

export const studentInsightQueryOptions = (classId: number, student: StudentOverview | null) => {
  const studentKey = student?.studentId || student?.name || ''
  return queryOptions({
    queryKey: teachingKeys.studentInsight(classId, studentKey),
    queryFn: () => {
      if (!student) throw new Error('Student is required')
      return getStudentInsight(classId, student)
    },
    enabled: classId > 0 && student !== null,
    staleTime: 30_000,
  })
}

export const confusionSignalsQueryOptions = (classId: number) =>
  queryOptions({
    queryKey: teachingKeys.confusions(classId),
    queryFn: () => getConfusionSignals(classId),
    enabled: classId > 0,
    staleTime: 30_000,
  })

export const reclassificationQueryOptions = (classId: number, taskId: string) =>
  queryOptions({
    queryKey: teachingKeys.reclassification(classId, taskId),
    queryFn: () => getReclassificationTask(classId, taskId),
    enabled: classId > 0 && Boolean(taskId),
  })

export const preLessonQueryOptions = (classId: number) =>
  queryOptions({
    queryKey: teachingKeys.preLesson(classId),
    queryFn: () => getPreLessonOverview(classId),
    enabled: classId > 0,
    staleTime: 30_000,
  })

export const timelineQueryOptions = (classId: number) =>
  queryOptions({
    queryKey: teachingKeys.timeline(classId),
    queryFn: () => getTimeline(classId),
    enabled: classId > 0,
    staleTime: 30_000,
  })

export const previewTasksQueryOptions = (classId: number) =>
  queryOptions({
    queryKey: teachingKeys.previews(classId),
    queryFn: () => listPreviewTasks(classId),
    enabled: classId > 0,
    staleTime: 30_000,
  })

export const previewTaskQueryOptions = (taskId: number) =>
  queryOptions({
    queryKey: teachingKeys.preview(taskId),
    queryFn: () => getPreviewTask(taskId),
    enabled: taskId > 0,
    staleTime: 60_000,
  })

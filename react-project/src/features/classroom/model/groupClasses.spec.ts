import { describe, expect, it } from 'vitest'
import { buildCourseGroups, filterCourseGroups } from './groupClasses'
import type { ClassGroupResponse, Course } from './types'

const courses: Course[] = [
  {
    id: 10,
    name: '高中数学',
    systemPrompt: '',
    knowledgeScope: '',
    teacherId: 1,
    createdAt: '2026-01-01T00:00:00',
    updatedAt: '2026-01-01T00:00:00',
  },
  {
    id: 20,
    name: '高中物理',
    systemPrompt: '',
    knowledgeScope: '',
    teacherId: 1,
    createdAt: '2026-01-01T00:00:00',
    updatedAt: '2026-01-01T00:00:00',
  },
]

const classGroups: ClassGroupResponse[] = [
  {
    courseGroup: '高中数学',
    courseId: 10,
    classes: [
      {
        id: 1,
        name: '数学归档班',
        courseGroup: '',
        courseId: null,
        qqGroupId: '',
        description: '',
        studentCount: 8,
        inviteCode: 'OLD001',
        status: 'ARCHIVED',
        createdAt: '2026-07-02T00:00:00',
      },
      {
        id: 2,
        name: '数学实验班',
        courseGroup: '高中数学',
        courseId: 10,
        qqGroupId: '',
        description: '',
        studentCount: 32,
        inviteCode: 'NEW001',
        status: 'ACTIVE',
        createdAt: '2026-07-01T00:00:00',
      },
    ],
  },
  {
    courseGroup: null,
    courseId: null,
    classes: [
      {
        id: 3,
        name: '临时班',
        courseGroup: '',
        courseId: null,
        qqGroupId: '',
        description: '',
        studentCount: 2,
        inviteCode: 'TMP001',
        status: 'ACTIVE',
        createdAt: '2026-07-03T00:00:00',
      },
    ],
  },
]

describe('classroom grouping', () => {
  it('adds empty courses, inherits group metadata, and keeps archived classes last', () => {
    const groups = buildCourseGroups(classGroups, courses)

    expect(groups.map((group) => group.name)).toEqual(['高中数学', '高中物理', '未分组班级'])
    expect(groups[0]?.classes.map((item) => item.name)).toEqual(['数学实验班', '数学归档班'])
    expect(groups[0]).toMatchObject({ totalStudents: 40, activeCount: 1, courseId: 10 })
    expect(groups[1]?.classes).toEqual([])
    expect(groups[2]?.classes[0]).toMatchObject({ name: '临时班', courseId: null })
  })

  it('filters classes by either class or course name', () => {
    const groups = buildCourseGroups(classGroups, courses)

    expect(filterCourseGroups(groups, '实验').flatMap((group) => group.classes)).toHaveLength(1)
    expect(filterCourseGroups(groups, '高中数学')[0]?.classes).toHaveLength(2)
    expect(filterCourseGroups(groups, '不存在')).toEqual([])
  })
})

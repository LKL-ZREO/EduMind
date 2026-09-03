import { useEffect, useMemo, useState } from 'react'
import {
  App,
  Alert,
  Button,
  Card,
  Col,
  Empty,
  Input,
  Row,
  Skeleton,
  Space,
  Tag,
  Typography,
} from 'antd'
import { useQuery } from '@tanstack/react-query'
import { useNavigate, useSearchParams } from 'react-router'
import {
  classGroupsQueryOptions,
  coursePresetsQueryOptions,
  coursesQueryOptions,
} from '@/features/classroom/api/classroomQueries'
import { ClassCard } from '@/features/classroom/components/ClassCard'
import {
  ClassEditorModal,
  type ClassFormValues,
} from '@/features/classroom/components/ClassEditorModal'
import { CourseEditorModal } from '@/features/classroom/components/CourseEditorModal'
import { InviteModal } from '@/features/classroom/components/InviteModal'
import {
  useCourseMutations,
  useCreateClassMutation,
} from '@/features/classroom/hooks/useClassroomMutations'
import {
  buildCourseGroups,
  filterCourseGroups,
  flattenClassGroups,
} from '@/features/classroom/model/groupClasses'
import type {
  ClassItem,
  ClassPayload,
  Course,
  CourseGroup,
  CoursePayload,
} from '@/features/classroom/model/types'
import { getApiErrorMessage } from '@/shared/api/errors'
import styles from './ClassListPage.module.css'

type ClassEditorState = {
  open: boolean
  initialValues?: Partial<ClassFormValues>
}

type InviteState = {
  open: boolean
  className: string
  inviteCode: string
}

const EMPTY_COURSES: Course[] = []

export function ClassListPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const { message } = App.useApp()
  const classGroupsQuery = useQuery(classGroupsQueryOptions())
  const coursesQuery = useQuery(coursesQueryOptions())
  const presetsQuery = useQuery(coursePresetsQueryOptions())
  const createClassMutation = useCreateClassMutation()
  const courseMutations = useCourseMutations()
  const [searchDraft, setSearchDraft] = useState(() => searchParams.get('q') || '')
  const [classEditor, setClassEditor] = useState<ClassEditorState>({ open: false })
  const [courseEditor, setCourseEditor] = useState<{ open: boolean; course: Course | null }>({
    open: false,
    course: null,
  })
  const [invite, setInvite] = useState<InviteState>({
    open: false,
    className: '',
    inviteCode: '',
  })

  const courses = coursesQuery.data || EMPTY_COURSES
  const groups = useMemo(
    () => buildCourseGroups(classGroupsQuery.data || [], courses),
    [classGroupsQuery.data, courses],
  )
  const filteredGroups = useMemo(
    () => filterCourseGroups(groups, searchDraft),
    [groups, searchDraft],
  )
  const classes = useMemo(
    () => flattenClassGroups(classGroupsQuery.data || []),
    [classGroupsQuery.data],
  )

  useEffect(() => {
    const timeout = window.setTimeout(() => {
      setSearchParams(
        (current) => {
          const next = new URLSearchParams(current)
          if (searchDraft) next.set('q', searchDraft)
          else next.delete('q')
          return next
        },
        { replace: true },
      )
    }, 200)
    return () => window.clearTimeout(timeout)
  }, [searchDraft, setSearchParams])

  function openClassEditor(group?: CourseGroup) {
    setClassEditor({
      open: true,
      initialValues: group
        ? { courseId: group.courseId || undefined, courseGroup: group.name }
        : undefined,
    })
  }

  async function handleCreateClass(payload: ClassPayload) {
    try {
      await createClassMutation.mutateAsync(payload)
      setClassEditor({ open: false })
      message.success('班级创建成功')
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, '创建班级失败'))
    }
  }

  async function handleSaveCourse(payload: CoursePayload) {
    try {
      if (courseEditor.course) {
        await courseMutations.updateCourse.mutateAsync({
          courseId: courseEditor.course.id,
          payload,
        })
        message.success('课程已更新')
      } else {
        await courseMutations.createCourse.mutateAsync(payload)
        message.success('课程创建成功')
      }
      setCourseEditor({ open: false, course: null })
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, '保存课程失败'))
    }
  }

  async function handleDeleteCourse() {
    const course = courseEditor.course
    if (!course) return
    try {
      await courseMutations.deleteCourse.mutateAsync(course.id)
      setCourseEditor({ open: false, course: null })
      message.success('课程已删除')
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, '删除课程失败'))
      throw error
    }
  }

  function openCourseSettings(group: CourseGroup) {
    const course = courses.find((item) => item.id === group.courseId) || null
    if (!course) {
      message.warning('未找到对应课程信息')
      return
    }
    setCourseEditor({ open: true, course })
  }

  function openInvite(classItem: ClassItem) {
    setInvite({
      open: true,
      className: classItem.name,
      inviteCode: classItem.inviteCode,
    })
  }

  const loading = classGroupsQuery.isPending || coursesQuery.isPending

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <div>
          <Typography.Title level={2}>班级管理</Typography.Title>
          <Typography.Text type="secondary">
            {groups.length} 门课程 / {classes.length} 个班级
          </Typography.Text>
        </div>
        <Space wrap>
          <Input.Search
            allowClear
            value={searchDraft}
            placeholder="搜索班级或课程"
            onChange={(event) => {
              setSearchDraft(event.target.value)
            }}
            style={{ width: 240 }}
          />
          <Button onClick={() => setCourseEditor({ open: true, course: null })}>创建课程</Button>
          <Button type="primary" onClick={() => openClassEditor()}>
            创建班级
          </Button>
        </Space>
      </header>

      {loading && (
        <Row gutter={[20, 20]}>
          {[1, 2, 3].map((item) => (
            <Col xs={24} md={12} xl={8} key={item}>
              <Card variant="borderless">
                <Skeleton active />
              </Card>
            </Col>
          ))}
        </Row>
      )}

      {classGroupsQuery.isError && (
        <Alert
          type="error"
          showIcon
          message="班级加载失败"
          description={getApiErrorMessage(classGroupsQuery.error, '无法加载班级列表')}
          action={<Button onClick={() => void classGroupsQuery.refetch()}>重试</Button>}
        />
      )}

      {!loading && !classGroupsQuery.isError && filteredGroups.length === 0 && (
        <Empty description={searchDraft ? '没有匹配的班级' : '暂无班级或课程'}>
          {!searchDraft && (
            <Button type="primary" onClick={() => openClassEditor()}>
              创建第一个班级
            </Button>
          )}
        </Empty>
      )}

      {!loading &&
        !classGroupsQuery.isError &&
        filteredGroups.map((group) => (
          <section className={styles.courseSection} key={group.key}>
            <div className={styles.courseHeader}>
              <div>
                <Space wrap>
                  <Typography.Title level={3}>{group.name}</Typography.Title>
                  <Tag>{group.classes.length} 个班级</Tag>
                  <Tag color="green">{group.activeCount} 个进行中</Tag>
                  <Tag color="blue">{group.totalStudents} 名学生</Tag>
                </Space>
              </div>
              <Space>
                {group.courseId && group.name !== '未分组班级' && (
                  <Button size="small" onClick={() => openCourseSettings(group)}>
                    课程设置
                  </Button>
                )}
                <Button size="small" type="primary" ghost onClick={() => openClassEditor(group)}>
                  添加班级
                </Button>
              </Space>
            </div>

            {group.classes.length === 0 ? (
              <Card variant="borderless">
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="这门课程下还没有班级">
                  <Button onClick={() => openClassEditor(group)}>添加班级</Button>
                </Empty>
              </Card>
            ) : (
              <Row gutter={[20, 20]}>
                {group.classes.map((classItem) => (
                  <Col xs={24} md={12} xl={8} key={classItem.id}>
                    <ClassCard
                      classItem={classItem}
                      onInvite={openInvite}
                      onManage={(classId) => void navigate(`/teacher/classes/${classId}`)}
                    />
                  </Col>
                ))}
              </Row>
            )}
          </section>
        ))}

      <ClassEditorModal
        open={classEditor.open}
        mode="create"
        courses={courses}
        initialValues={classEditor.initialValues}
        submitting={createClassMutation.isPending}
        onCancel={() => setClassEditor({ open: false })}
        onSubmit={handleCreateClass}
      />

      <CourseEditorModal
        open={courseEditor.open}
        course={courseEditor.course}
        presets={presetsQuery.data || {}}
        submitting={
          courseMutations.createCourse.isPending || courseMutations.updateCourse.isPending
        }
        deleting={courseMutations.deleteCourse.isPending}
        onCancel={() => setCourseEditor({ open: false, course: null })}
        onSubmit={handleSaveCourse}
        onDelete={handleDeleteCourse}
      />

      <InviteModal
        open={invite.open}
        className={invite.className}
        inviteCode={invite.inviteCode}
        onClose={() => setInvite((current) => ({ ...current, open: false }))}
      />
    </main>
  )
}

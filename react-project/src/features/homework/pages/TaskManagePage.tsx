import { useMemo, useState } from 'react'
import {
  App,
  Button,
  Card,
  Checkbox,
  Input,
  InputNumber,
  Space,
  Statistic,
  Switch,
  Typography,
} from 'antd'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { classGroupsQueryOptions } from '@/features/classroom/api/classroomQueries'
import {
  homeworkDraftsQueryOptions,
  homeworkQuestionBankQueryOptions,
  teacherTasksQueryOptions,
} from '@/features/homework/api/homeworkQueries'
import { QuestionBankPanel } from '@/features/homework/components/QuestionBankPanel'
import { QuestionEditorCard } from '@/features/homework/components/QuestionEditorCard'
import { TaskLibrarySidebar } from '@/features/homework/components/TaskLibrarySidebar'
import { useTeacherHomeworkMutations } from '@/features/homework/hooks/useHomeworkMutations'
import {
  buildDraftDescription,
  cloneQuestion,
  createBlankQuestion,
  formatDateTime,
  groupTasks,
  toDatetimeLocal,
  totalDraftScore,
} from '@/features/homework/model/homework'
import type {
  DraftQuestion,
  HomeworkDraft,
  SaveDraftPayload,
  TaskGroup,
  TeachingQuestion,
} from '@/features/homework/model/types'
import { getApiErrorMessage } from '@/shared/api/errors'
import { sanitizeHtml } from '@/shared/utils/safeHtml'
import styles from './TaskManagePage.module.css'

type DraftEditor = {
  id: number | null
  taskName: string
  deadline: string
  allowLate: boolean
  latePenalty: number
  questions: DraftQuestion[]
}

function emptyDraft(): DraftEditor {
  return {
    id: null,
    taskName: '',
    deadline: '',
    allowLate: true,
    latePenalty: 0,
    questions: [createBlankQuestion()],
  }
}

function editorFromDraft(draft: HomeworkDraft): DraftEditor {
  return {
    id: draft.id,
    taskName: draft.taskName || '',
    deadline: toDatetimeLocal(draft.deadline),
    allowLate: draft.allowLate ?? true,
    latePenalty: draft.latePenalty ?? 0,
    questions: draft.questions.length
      ? draft.questions.map(cloneQuestion)
      : [createBlankQuestion()],
  }
}

export function TaskManagePage() {
  const { message, modal } = App.useApp()
  const navigate = useNavigate()
  const classGroupsQuery = useQuery(classGroupsQueryOptions())
  const classes = useMemo(
    () =>
      (classGroupsQuery.data || []).flatMap((group) =>
        group.classes.map((classItem) => ({ id: classItem.id, name: classItem.name })),
      ),
    [classGroupsQuery.data],
  )
  const classIds = useMemo(() => classes.map((item) => item.id), [classes])
  const tasksQuery = useQuery(teacherTasksQueryOptions(classIds))
  const draftsQuery = useQuery(homeworkDraftsQueryOptions())
  const [questionInput, setQuestionInput] = useState('')
  const [questionKeyword, setQuestionKeyword] = useState('')
  const questionBankQuery = useQuery(homeworkQuestionBankQueryOptions(questionKeyword))
  const mutations = useTeacherHomeworkMutations()
  const [mode, setMode] = useState<'draft' | 'published'>('draft')
  const [draft, setDraft] = useState<DraftEditor>(emptyDraft)
  const [activePublishedKey, setActivePublishedKey] = useState('')
  const [draftSearch, setDraftSearch] = useState('')
  const [publishedSearch, setPublishedSearch] = useState('')
  const [selectedClassIds, setSelectedClassIds] = useState<number[] | null>(null)

  const taskGroups = useMemo(
    () => groupTasks(tasksQuery.data || [], classes),
    [classes, tasksQuery.data],
  )
  const activeGroup = taskGroups.find((group) => group.key === activePublishedKey) || taskGroups[0]
  const effectiveSelectedClassIds = selectedClassIds ?? (classes[0] ? [classes[0].id] : [])
  const selectedClasses = classes.filter((item) => effectiveSelectedClassIds.includes(item.id))
  const score = totalDraftScore(draft.questions)

  function startNewDraft() {
    setMode('draft')
    setDraft(emptyDraft())
  }

  function selectDraft(item: HomeworkDraft) {
    setMode('draft')
    setDraft(editorFromDraft(item))
  }

  function selectPublished(group: TaskGroup) {
    setMode('published')
    setActivePublishedKey(group.key)
  }

  function copyPublishedAsDraft(group: TaskGroup) {
    setMode('draft')
    setDraft({
      id: null,
      taskName: `${group.taskName} 副本`,
      deadline: toDatetimeLocal(group.deadline),
      allowLate: group.allowLate,
      latePenalty: group.latePenalty,
      questions: [
        {
          type: 'HOMEWORK',
          title: group.taskName,
          requirement: group.description || '',
          score: 100,
          uploadRequired: true,
        },
      ],
    })
    message.success('已复制为未保存草稿')
  }

  function updateQuestion(index: number, question: DraftQuestion) {
    setDraft((current) => ({
      ...current,
      questions: current.questions.map((item, itemIndex) =>
        itemIndex === index ? question : item,
      ),
    }))
  }

  function removeQuestion(index: number) {
    if (draft.questions.length <= 1) {
      message.warning('至少保留一道题')
      return
    }
    setDraft((current) => ({
      ...current,
      questions: current.questions.filter((_, itemIndex) => itemIndex !== index),
    }))
  }

  function addFromBank(question: TeachingQuestion) {
    setDraft((current) => ({
      ...current,
      questions: [...current.questions, cloneQuestion(question)],
    }))
    setMode('draft')
    message.success('已加入当前草稿')
  }

  function draftPayload(): SaveDraftPayload {
    return {
      taskName: draft.taskName.trim() || '未命名作业',
      description: buildDraftDescription(draft.questions),
      deadline: draft.deadline || null,
      allowLate: draft.allowLate,
      latePenalty: draft.latePenalty,
      questions: draft.questions,
    }
  }

  async function saveCurrentDraft(showSuccess = true) {
    try {
      const saved = await mutations.saveDraft.mutateAsync({
        payload: draftPayload(),
        draftId: draft.id,
      })
      setDraft(editorFromDraft(saved))
      if (showSuccess) message.success('草稿已保存，题目已同步到题库')
      return saved
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, '保存草稿失败'))
      return null
    }
  }

  function confirmDeleteDraft() {
    if (!draft.id) {
      startNewDraft()
      return
    }
    modal.confirm({
      title: '删除这个草稿？',
      content: '删除不会影响已经发布的作业。',
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await mutations.deleteDraft.mutateAsync(draft.id!)
          setDraft(emptyDraft())
          message.success('草稿已删除')
        } catch (error: unknown) {
          message.error(getApiErrorMessage(error, '删除草稿失败'))
          throw error
        }
      },
    })
  }

  async function publishCurrentDraft() {
    if (!draft.taskName.trim()) return message.warning('请先填写作业名称')
    if (!draft.questions.length) return message.warning('请至少添加一道题')
    if (!draft.deadline) return message.warning('发布前需要设置截止时间')
    if (!effectiveSelectedClassIds.length) return message.warning('请选择至少一个班级')

    try {
      const saved = await saveCurrentDraft(false)
      if (!saved) return
      await mutations.publishDraft.mutateAsync({
        draftId: saved.id,
        payload: {
          classIds: effectiveSelectedClassIds,
          taskName: draft.taskName.trim(),
          deadline: draft.deadline,
          allowLate: draft.allowLate,
          latePenalty: draft.latePenalty,
        },
      })
      setMode('published')
      message.success(`已发布到 ${effectiveSelectedClassIds.length} 个班级`)
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, '发布作业失败'))
    }
  }

  return (
    <main className={styles.page}>
      <header className={styles.hero}>
        <div>
          <Typography.Text className={styles.eyebrow}>ASSIGNMENT STUDIO</Typography.Text>
          <Typography.Title>作业管理</Typography.Title>
          <Typography.Paragraph type="secondary">
            先沉淀草稿和题库，再选择班级与截止时间发布。
          </Typography.Paragraph>
        </div>
        <Space wrap>
          <Button onClick={startNewDraft}>新建草稿</Button>
          <Button
            type="primary"
            loading={mutations.saveDraft.isPending}
            onClick={() => void saveCurrentDraft()}
          >
            保存草稿
          </Button>
        </Space>
      </header>

      <section className={styles.summary}>
        <Card>
          <Statistic title="草稿" value={draftsQuery.data?.length || 0} />
        </Card>
        <Card>
          <Statistic title="题库题目" value={questionBankQuery.data?.length || 0} />
        </Card>
        <Card>
          <Statistic title="已发布套数" value={taskGroups.length} />
        </Card>
      </section>

      <section className={styles.workspace}>
        <TaskLibrarySidebar
          mode={mode}
          drafts={draftsQuery.data || []}
          groups={taskGroups}
          activeDraftId={draft.id}
          activePublishedKey={activeGroup?.key || ''}
          loading={tasksQuery.isLoading}
          draftSearch={draftSearch}
          publishedSearch={publishedSearch}
          onModeChange={setMode}
          onDraftSearch={setDraftSearch}
          onPublishedSearch={setPublishedSearch}
          onSelectDraft={selectDraft}
          onSelectPublished={selectPublished}
        />

        <section className={styles.editor}>
          {mode === 'published' && activeGroup ? (
            <>
              <div className={styles.sectionHead}>
                <div>
                  <Typography.Text className={styles.eyebrow}>PUBLISHED</Typography.Text>
                  <Typography.Title level={2}>{activeGroup.taskName}</Typography.Title>
                </div>
                <Space wrap>
                  <Button onClick={() => copyPublishedAsDraft(activeGroup)}>复制为草稿</Button>
                  <Button
                    type="primary"
                    onClick={() => void navigate(`/teacher/tasks/${activeGroup.tasks[0]?.id}`)}
                  >
                    查看统计
                  </Button>
                </Space>
              </div>
              <div className={styles.metadata}>
                <div>
                  <span>班级</span>
                  <strong>{activeGroup.classNames.join('、')}</strong>
                </div>
                <div>
                  <span>截止</span>
                  <strong>{formatDateTime(activeGroup.deadline)}</strong>
                </div>
                <div>
                  <span>迟交</span>
                  <strong>
                    {activeGroup.allowLate
                      ? `允许，每天扣 ${activeGroup.latePenalty} 分`
                      : '不允许'}
                  </strong>
                </div>
              </div>
              <div
                className={styles.preview}
                dangerouslySetInnerHTML={{ __html: sanitizeHtml(activeGroup.description) }}
              />
            </>
          ) : (
            <>
              <div className={styles.sectionHead}>
                <div>
                  <Typography.Text className={styles.eyebrow}>
                    {draft.id ? 'SAVED DRAFT' : 'NEW DRAFT'}
                  </Typography.Text>
                  <Typography.Title level={2}>{draft.taskName || '未命名草稿'}</Typography.Title>
                </div>
                <Space>
                  <Button danger onClick={confirmDeleteDraft}>
                    删除草稿
                  </Button>
                  <Button
                    type="primary"
                    loading={mutations.saveDraft.isPending}
                    onClick={() => void saveCurrentDraft()}
                  >
                    保存草稿
                  </Button>
                </Space>
              </div>
              <div className={styles.formGrid}>
                <label>
                  <Typography.Text strong>作业名称</Typography.Text>
                  <Input
                    value={draft.taskName}
                    placeholder="例如：第三次作业：数组与链表"
                    onChange={(event) =>
                      setDraft((current) => ({ ...current, taskName: event.target.value }))
                    }
                  />
                </label>
                <label>
                  <Typography.Text strong>截止时间</Typography.Text>
                  <Input
                    type="datetime-local"
                    value={draft.deadline}
                    onChange={(event) =>
                      setDraft((current) => ({ ...current, deadline: event.target.value }))
                    }
                  />
                </label>
              </div>
              <Space wrap className={styles.lateSetting}>
                <Switch
                  checked={draft.allowLate}
                  onChange={(allowLate) => setDraft((current) => ({ ...current, allowLate }))}
                />
                <span>允许迟交，每天扣</span>
                <InputNumber
                  min={0}
                  max={100}
                  value={draft.latePenalty}
                  disabled={!draft.allowLate}
                  onChange={(latePenalty) =>
                    setDraft((current) => ({ ...current, latePenalty: latePenalty ?? 0 }))
                  }
                />
                <span>分</span>
              </Space>
              <div className={styles.questionHead}>
                <Typography.Title level={3}>
                  {draft.questions.length} 题 · {score} 分
                </Typography.Title>
                <Button
                  type="primary"
                  onClick={() =>
                    setDraft((current) => ({
                      ...current,
                      questions: [...current.questions, createBlankQuestion()],
                    }))
                  }
                >
                  ＋新题目
                </Button>
              </div>
              <div className={styles.questions}>
                {draft.questions.map((question, index) => (
                  <QuestionEditorCard
                    key={`${question.id || 'new'}-${index}`}
                    index={index}
                    question={question}
                    removable={draft.questions.length > 1}
                    onChange={(next) => updateQuestion(index, next)}
                    onRemove={() => removeQuestion(index)}
                  />
                ))}
              </div>
            </>
          )}
        </section>

        <aside className={styles.rightPanel}>
          <QuestionBankPanel
            questions={questionBankQuery.data || []}
            input={questionInput}
            loading={questionBankQuery.isFetching}
            onInputChange={setQuestionInput}
            onSearch={() => setQuestionKeyword(questionInput.trim())}
            onAdd={addFromBank}
          />
          <section className={styles.publishBox}>
            <Typography.Text className={styles.eyebrow}>PUBLISH</Typography.Text>
            <Typography.Title level={4}>发布设置</Typography.Title>
            <Checkbox.Group
              className={styles.classOptions}
              value={effectiveSelectedClassIds}
              options={classes.map((item) => ({ label: item.name, value: item.id }))}
              onChange={setSelectedClassIds}
            />
            <div className={styles.publishPreview}>
              <strong>{selectedClasses.length} 个班级</strong>
              <span>{selectedClasses.map((item) => item.name).join('、') || '尚未选择班级'}</span>
              <span>截止 {formatDateTime(draft.deadline)}</span>
            </div>
            <Button
              block
              type="primary"
              loading={mutations.publishDraft.isPending}
              disabled={mode === 'published'}
              onClick={() => void publishCurrentDraft()}
            >
              发布当前草稿
            </Button>
          </section>
        </aside>
      </section>
    </main>
  )
}

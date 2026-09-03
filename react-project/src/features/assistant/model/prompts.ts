import type { ChatMode } from './types'

export type ContextualPrompt = {
  icon: string
  title: string
  description: string
  mode: ChatMode
  prompt: string
}

export function getContextualPrompts(className = '当前班级'): ContextualPrompt[] {
  return [
    {
      icon: '🧭',
      title: '生成备课方案',
      description: '结合班级薄弱点与知识库形成可落地教案',
      mode: 'lesson_plan',
      prompt: `请结合${className}的近期学情和课程知识库，生成一份20分钟的针对性复习课方案，包含教学目标、重难点、教学流程、互动问题和课后练习。`,
    },
    {
      icon: '📊',
      title: '分析班级学情',
      description: '查询实时数据并给出教学建议',
      mode: 'learning_analysis',
      prompt: `请分析${className}当前整体学情、薄弱知识点和需要重点关注的问题，并给出下一步教学建议。`,
    },
    {
      icon: '🧩',
      title: '生成分层练习',
      description: '从知识库生成基础、提高和挑战题',
      mode: 'auto',
      prompt: `请根据${className}当前薄弱知识点生成6道分层练习，分为基础、提高、挑战三档，并附参考答案与解析。`,
    },
    {
      icon: '👤',
      title: '诊断单个学生',
      description: '输入姓名后查询成绩趋势和薄弱点',
      mode: 'learning_analysis',
      prompt:
        '请先询问我要分析的学生姓名，然后查询其最近成绩趋势、薄弱知识点，并给出个性化辅导建议。',
    },
  ]
}

export const CHAT_MODE_OPTIONS: Array<{ value: ChatMode; label: string }> = [
  { value: 'auto', label: '自动模式' },
  { value: 'lesson_plan', label: '备课方案' },
  { value: 'learning_analysis', label: '学情分析' },
  { value: 'grading', label: '作业批改' },
]

<template>
  <div class="dashboard">
    <!-- 查询区域 -->
    <el-card class="card">
      <template #header>分数查询</template>

      <el-form :inline="true">
        <el-form-item label="班级">
          <el-select v-model="query.classId" placeholder="请选择班级">
            <el-option label="高一(1)班" value="1" />
            <el-option label="高一(2)班" value="2" />
          </el-select>
        </el-form-item>

        <el-form-item label="科目">
          <el-select v-model="query.subject" placeholder="请选择科目">
            <el-option label="数学" value="math" />
            <el-option label="语文" value="chinese" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="scores" size="small">
        <el-table-column prop="name" label="姓名" />
        <el-table-column prop="score" label="分数" />
        <el-table-column prop="rank" label="排名" />
      </el-table>
    </el-card>

    <!-- 统计区域 -->
    <el-row :gutter="16" class="stats">
      <el-col :span="8">
        <el-card>
          <div>班级平均分</div>
          <h2>{{ avgScore }}</h2>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <div>最高分</div>
          <h2>{{ maxScore }}</h2>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <div>最低分</div>
          <h2>{{ minScore }}</h2>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-card class="chart-card">
      <template #header>成绩分布图</template>
      <div ref="chartRef" class="chart"></div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import * as echarts from 'echarts'
import { getScores, getAverage, getDistribution } from '@/api/teacher'

const query = reactive({
  classId: '1',
  subject: 'math'
})

const scores = ref([])
const avgScore = ref(0)
const maxScore = ref(0)
const minScore = ref(0)
const chartRef = ref(null)

const loadData = async () => {
  try {
    // 获取分数列表
    const scoreRes = await getScores(query.classId, query.subject)
    scores.value = scoreRes.data

    // 获取平均分统计
    const avgRes = await getAverage(query.classId, query.subject)
    avgScore.value = avgRes.data.avg.toFixed(1)
    maxScore.value = avgRes.data.max
    minScore.value = avgRes.data.min

    // 获取分布数据
    const distRes = await getDistribution(query.classId, query.subject)
    initChart(distRes.data)
  } catch (error) {
    console.error('加载数据失败', error)
  }
}

const initChart = (distributionData) => {
  const chart = echarts.init(chartRef.value)
  const xData = Object.keys(distributionData)
  const yData = Object.values(distributionData)

  chart.setOption({
    tooltip: {},
    xAxis: {
      type: 'category',
      data: xData
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '人数',
        type: 'bar',
        data: yData,
        itemStyle: { color: '#409EFF' }
      }
    ]
  })
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.dashboard {
  padding: 16px;
}

.card {
  margin-bottom: 16px;
}

.stats {
  margin-bottom: 16px;
}

.chart-card {
  margin-top: 8px;
}

.chart {
  height: 320px;
}
</style>

package com.wmm.ledger.data

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal const val SCHEDULED_BILL_JOB_ID = 124

class ScheduledBillJob : JobService() {
    private var task: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        task = CoroutineScope(Dispatchers.Main).launch {
            val retry = try {
                LedgerRepository.shared(applicationContext).recordScheduledBills()
                false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                true
            }
            jobFinished(params, retry)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        task?.cancel()
        return true
    }

    companion object {
        internal fun update(context: Context, active: Boolean): Boolean {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            val pending = scheduler.getPendingJob(SCHEDULED_BILL_JOB_ID)
            if (active == (pending != null)) return true
            if (!active) {
                scheduler.cancel(SCHEDULED_BILL_JOB_ID)
                return true
            }
            // 定时账单按日历日期记账，由系统在后台合并调度，前台打开时也检查漏记日期。
            val job = JobInfo.Builder(SCHEDULED_BILL_JOB_ID, ComponentName(context, ScheduledBillJob::class.java))
                .setPeriodic(TimeUnit.HOURS.toMillis(1))
                .setPersisted(true)
                .build()
            return scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS
        }
    }
}

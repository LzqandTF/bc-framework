package cn.bc.scheduler.service;

import cn.bc.BCConstants;
import cn.bc.core.exception.CoreException;
import cn.bc.scheduler.domain.ScheduleJob;
import cn.bc.scheduler.spring.MethodInvokingJobEx;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.MethodInvoker;

import java.util.Date;
import java.util.List;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.JobKey.jobKey;
import static org.quartz.TriggerBuilder.newTrigger;
import static org.quartz.TriggerKey.triggerKey;

/**
 * 调度任务管理器的实现，注意该类不要配置事务管理
 * <p>
 * 该类在初始化时就会自动将可用的调度任务推送到调度计划中执行
 * </p>
 *
 * @author dragon
 * @since 2011-08-30
 * @ref http://quartz-scheduler.org/documentation/quartz-2.x/migration-guide
 */
public class SchedulerManageImpl implements SchedulerManage, ApplicationContextAware, InitializingBean {
	private static final Logger logger = LoggerFactory.getLogger(SchedulerManageImpl.class);
	private Scheduler scheduler;
	private SchedulerService schedulerService;
	private ApplicationContext applicationContext;
	private boolean disabled = false;

	public boolean isDisabled() {
		return disabled;
	}

	public void setDisabled(boolean disabled) {
		this.disabled = disabled;
	}

	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	public void setScheduler(Scheduler scheduler) {
		this.scheduler = scheduler;
	}

	public void setSchedulerService(SchedulerService schedulerService) {
		this.schedulerService = schedulerService;
	}

	public void afterPropertiesSet() throws Exception {
		// 禁用就不再处理
		if (isDisabled()) {
			logger.warn("SchedulerManage was config to disabled");
			return;
		}

		// 立即计划所有可用的调度任务
		List<ScheduleJob> jobs = this.schedulerService.findAllEnabledScheduleJob();
		logger.warn("scheduling {} jobs", jobs.size());
		for (ScheduleJob job : jobs) {
			this.scheduleJob(job.getId());
		}
	}

	public Date scheduleJob(Long scheduleJobId) throws Exception {
		ScheduleJob scheduleJob = this.schedulerService.loadScheduleJob(scheduleJobId);
		if (scheduleJob == null) {
			logger.warn("ignore unknown scheduleJobId: {}", scheduleJobId);
			return null;
		}
		logger.warn("scheduling job:" + scheduleJob.toString());

		Date nextDate;
		// 检测是否已经调度
		CronTrigger trigger = (CronTrigger) this.scheduler.getTrigger(
				new TriggerKey(scheduleJob.getTriggerName(), scheduleJob.getGroupn()));
		if (null == trigger) {
			// Trigger不存在，就创建一个新的
			MethodInvoker methodInvoker = new MethodInvoker();
			methodInvoker.setTargetObject(this.applicationContext.getBean(scheduleJob.getBean()));
			methodInvoker.setTargetMethod(scheduleJob.getMethod());
			methodInvoker.prepare();

			// 记录状态数据
			JobDataMap jobDataMap = new JobDataMap();
			jobDataMap.put("methodInvoker", methodInvoker);// 记录方法调用器
			jobDataMap.put("scheduleJob", scheduleJob);// 记录配置信息
			jobDataMap.put("schedulerService", this.schedulerService);
			JobDetail jobDetail = newJob(MethodInvokingJobEx.class)
					.withIdentity(scheduleJob.getName(), scheduleJob.getGroupn())
					.usingJobData(jobDataMap)
					.build();

			trigger = newTrigger()
					.withIdentity(scheduleJob.getTriggerName(), scheduleJob.getGroupn())
					.withSchedule(cronSchedule(scheduleJob.getCron()))
					.build();
			nextDate = this.scheduler.scheduleJob(jobDetail, trigger);
		} else {
			// Trigger已存在，更新相应的调度设置
			//trigger.setCronExpression(scheduleJob.getCron());
			TriggerKey key = trigger.getKey();
			trigger = newTrigger()
					.withIdentity(key)
					.withSchedule(cronSchedule(scheduleJob.getCron()))
					.build();
			nextDate = this.scheduler.rescheduleJob(key, trigger);
		}

		// 将任务的状态设置为正常
		if (scheduleJob.getStatus() != BCConstants.STATUS_ENABLED) {
			scheduleJob.setStatus(BCConstants.STATUS_ENABLED);
			this.schedulerService.saveScheduleJob(scheduleJob);
		}

		return nextDate;
	}

	public Date rescheduleJob(Long scheduleJobId) throws Exception {
		ScheduleJob scheduleJob = this.schedulerService.loadScheduleJob(scheduleJobId);
		if (scheduleJob == null) {
			logger.warn("ignore unknown scheduleJobId: {}", scheduleJobId);
			return null;
		}

		Date nextDate;
		// 检测是否已经调度
		CronTrigger trigger = (CronTrigger) this.scheduler.getTrigger(
				triggerKey(scheduleJob.getTriggerName(), scheduleJob.getGroupn()));
		if (null == trigger) {
			throw new CoreException("trigger not exist:scheduleJobId=" + scheduleJobId);
		} else {
			// Trigger已存在，更新相应的调度设置
			// trigger.setCronExpression(scheduleJob.getCron());
			TriggerKey key = trigger.getKey();
			trigger = newTrigger()
					.withIdentity(key)
					.withSchedule(cronSchedule(scheduleJob.getCron()))
					.build();
			nextDate = this.scheduler.rescheduleJob(key, trigger);
		}

		// 将任务的状态设置为正常
		if (scheduleJob.getStatus() != BCConstants.STATUS_ENABLED) {
			scheduleJob.setStatus(BCConstants.STATUS_ENABLED);
			this.schedulerService.saveScheduleJob(scheduleJob);
		}

		return nextDate;
	}

	public void stopJob(Long scheduleJobId) throws Exception {
		ScheduleJob scheduleJob = this.schedulerService
				.loadScheduleJob(scheduleJobId);
		if (scheduleJob == null) {
			logger.warn("ignore unknown scheduleJobId: {}", scheduleJobId);
			return;
		}

		// 删除调度
		CronTrigger trigger = (CronTrigger) this.scheduler.getTrigger(
				triggerKey(scheduleJob.getTriggerName(), scheduleJob.getGroupn()));
		if (null != trigger) {
			this.scheduler.deleteJob(jobKey(scheduleJob.getName(), scheduleJob.getGroupn()));
		}

		// 将任务的状态设置为禁用
		if (scheduleJob.getStatus() != BCConstants.STATUS_DISABLED) {
			scheduleJob.setStatus(BCConstants.STATUS_DISABLED);
			this.schedulerService.saveScheduleJob(scheduleJob);
		}
	}

	public void deleteJob(Long scheduleJobId) throws Exception {
		ScheduleJob scheduleJob = this.schedulerService.loadScheduleJob(scheduleJobId);
		if (scheduleJob == null) {
			logger.warn("ignore unknown scheduleJobId: {}", scheduleJobId);
			return;
		}

		// 删除调度
		CronTrigger trigger = (CronTrigger) this.scheduler.getTrigger(
				triggerKey(scheduleJob.getTriggerName(), scheduleJob.getGroupn()));
		if (null != trigger) {
			this.scheduler.deleteJob(jobKey(scheduleJob.getName(), scheduleJob.getGroupn()));
		}

		// 标记为删除状态
		scheduleJob.setStatus(BCConstants.STATUS_DELETED);
		this.schedulerService.saveScheduleJob(scheduleJob);
	}

	public void pauseJob(Long scheduleJobId) throws Exception {
		ScheduleJob scheduleJob = this.schedulerService.loadScheduleJob(scheduleJobId);
		if (scheduleJob == null) {
			logger.warn("ignore unknown scheduleJobId: {}", scheduleJobId);
			return;
		}

		// 暂停调度
		CronTrigger trigger = (CronTrigger) this.scheduler.getTrigger(
				triggerKey(scheduleJob.getTriggerName(), scheduleJob.getGroupn()));
		if (null != trigger) {
			this.scheduler.pauseJob(jobKey(scheduleJob.getName(), scheduleJob.getGroupn()));
		}
	}

	public void resumeJob(Long scheduleJobId) throws Exception {
		ScheduleJob scheduleJob = this.schedulerService.loadScheduleJob(scheduleJobId);
		if (scheduleJob == null) {
			logger.warn("ignore unknown scheduleJobId: {}", scheduleJobId);
			return;
		}

		// 恢复调度
		CronTrigger trigger = (CronTrigger) this.scheduler.getTrigger(
				triggerKey(scheduleJob.getTriggerName(), scheduleJob.getGroupn()));
		if (null != trigger) {
			this.scheduler.resumeJob(jobKey(scheduleJob.getName(), scheduleJob.getGroupn()));
		}
	}
}
// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.wireless.speed.speedometer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

import java.util.concurrent.Callable;

/**
 * A basic power manager implementation that decides whether a measurement can be scheduled
 * based on the current battery level: no measurements will be scheduled if the current battery
 * is lower than a threshold.
 * 
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class BatteryCapPowerManager {
  
  /** The application context needed to receive intent broadcasts */
  private Context context;
  /** The minimum threshold below which no measurements will be scheduled */
  private int minBatteryThreshold;
  /** Tells whether the phone is charging */
  private boolean isCharging;
  /** Current battery level in percentage */ 
  private int curBatteryLevel;
  /** Receiver that handles batter change broadcast intents */
  private BroadcastReceiver broadcastReceiver;
    
  public BatteryCapPowerManager(int batteryThresh, Context context) {
    this.minBatteryThreshold = batteryThresh;
    this.context = context;
    this.broadcastReceiver = new PowerStateChangeReceiver();
    // Registers a receiver for battery change events.
    Intent powerIntent = context.registerReceiver(broadcastReceiver, 
        new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    updateBatteryStat(powerIntent);
  }
  
  /** 
   * Sets the minimum battery percentage below which measurements cannot be run.
   * 
   * @param batteryCap the batter percentage threshold between 0 and 100
   */
  public synchronized void setBatteryThresh(int batteryThresh) throws IllegalArgumentException {
    if (batteryThresh < 0 || batteryThresh > 100) {
      throw new IllegalArgumentException("batteryCap must fall between 0 and 100, inclusive");
    }
    this.minBatteryThreshold = batteryThresh;
  }
  
  public synchronized int getBatteryThresh() {
    return this.minBatteryThreshold;
  }
  
  /** 
   * Returns whether a measurement can be run.
   */
  public synchronized boolean canScheduleExperiment() {
    return (isCharging || curBatteryLevel > minBatteryThreshold);
  }
  
  /** 
   * Part of stopping Speedometer: to unregister the broadcast receiver. 
   */
  public void stop() {
    Log.i(SpeedometerApp.TAG, "Unregistering BroadcastReceiver from ACTION_BATTERY_CHANGED");
    context.unregisterReceiver(broadcastReceiver);
  }
  
  private synchronized void updateBatteryStat(Intent powerIntent) {
    int scale = powerIntent.getIntExtra(BatteryManager.EXTRA_SCALE, Config.DEFAULT_BATTERY_SCALE);
    int level = powerIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, Config.DEFAULT_BATTERY_LEVEL);
    // change to the unit of percentage
    this.curBatteryLevel = (int) ((double) level * 100 / scale);
    this.isCharging = powerIntent.getIntExtra(BatteryManager.EXTRA_STATUS, 
        BatteryManager.BATTERY_STATUS_UNKNOWN) == BatteryManager.BATTERY_STATUS_CHARGING;
    
    Log.i(SpeedometerApp.TAG, 
        "Current power level is " + curBatteryLevel + " and isCharging = " + isCharging);
  }
  
  private class PowerStateChangeReceiver extends BroadcastReceiver {
    /** 
     * @see android.content.BroadcastReceiver#onReceive(android.content.Context, 
     * android.content.Intent)
     */
    @Override
    public void onReceive(Context context, Intent intent) {
      updateBatteryStat(intent);
    }
  }
  
  /**
   * A task wrapper that is power aware, the real logic is carried out by realTask
   * 
   * @author wenjiezeng@google.com (Steve Zeng)
   *
   */
  public static class PowerAwareTask implements Callable<MeasurementResult> {
    
    private MeasurementTask realTask;
    private BatteryCapPowerManager pManager;
    
    public PowerAwareTask(MeasurementTask task, BatteryCapPowerManager manager) {
      realTask = task;
      pManager = manager;
    }
    
    @Override
    public MeasurementResult call() throws MeasurementError {
      if (!pManager.canScheduleExperiment()) {
        throw new MeasurementError("Not enough power");
      }
      return realTask.call();
    }
  }
}

package org.usvm.ps.statistics

/**
 * An interface for objects that are subscribed to some [Statistics].
 */
interface StatisticsObserver {
    /**
     * This function is called inside [Statistics.notifyObserversAboutChange] to
     * update an instance of [StatisticsObserver] about the fact that some changes
     * occurred inside a statistics.
     */
    fun updateOnStatisticsChange()
}
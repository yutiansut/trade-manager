/* ===========================================================
 * TradeManager : An application to trade strategies for the Java(tm) platform
 * ===========================================================
 *
 * (C) Copyright 2011-2011, by Simon Allen and Contributors.
 *
 * Project Info:  org.trade
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 * [Java is a trademark or registered trademark of Oracle, Inc.
 * in the United States and other countries.]
 *
 * (C) Copyright 2011-2011, by Simon Allen and Contributors.
 *
 * Original Author:  Simon Allen;
 * Contributor(s):   -;
 *
 * Changes 
 * -------
 *
 */
package org.trade.strategy;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.trade.broker.BrokerModel;
import org.trade.core.util.TradingCalendar;
import org.trade.core.valuetype.Money;
import org.trade.dictionary.valuetype.Action;
import org.trade.dictionary.valuetype.Side;
import org.trade.dictionary.valuetype.TradestrategyStatus;
import org.trade.persistent.dao.Entrylimit;
import org.trade.strategy.data.CandleSeries;
import org.trade.strategy.data.StrategyData;
import org.trade.strategy.data.candle.CandleItem;

/**
 * @author Simon Allen
 * 
 * @version $Revision: 1.0 $
 */

public class FiveMinGapBarStrategy extends AbstractStrategyRule {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1373776942145894938L;
	private final static Logger _log = LoggerFactory
			.getLogger(FiveMinGapBarStrategy.class);

	/**
	 * Default Constructor
	 * 
	
	
	
	
	 * @param brokerManagerModel BrokerModel
	 * @param datasetContainer StrategyData
	 * @param idTradestrategy Integer
	 */

	public FiveMinGapBarStrategy(BrokerModel brokerManagerModel,
			StrategyData datasetContainer, Integer idTradestrategy) {
		super(brokerManagerModel, datasetContainer, idTradestrategy);

	}

	/*
	 * Note the current candle is just forming Enter a tier 1-3 gap in first
	 * 5min bar direction, with a 3R target and stop @ 5min high/low
	 * 
	 * @param candleSeries the series of candels that has been updated.
	 * 
	 * @param newBar has a new bar just started.
	 */
	/**
	 * Method runStrategy.
	 * @param candleSeries CandleSeries
	 * @param newBar boolean
	 * @see org.trade.strategy.StrategyRule#runStrategy(CandleSeries, boolean)
	 */
	public void runStrategy(CandleSeries candleSeries, boolean newBar) {

		try {
			if (getCurrentCandleCount() > 0) {
				// Get the current candle
				CandleItem currentCandleItem = (CandleItem) candleSeries
						.getDataItem(getCurrentCandleCount());
				Date startPeriod = currentCandleItem.getPeriod().getStart();

				/*
				 * Trade is open kill this Strategy as its job is done.
				 */
				if (this.isPositionOpen() || this.isPositionCancelled()) {
					_log.info("FiveMinGapBarStrategy complete open position filled symbol: "
							+ getSymbol() + " startPeriod: " + startPeriod);
					this.cancel();
					return;
				}
				/*
				 * Only manage trades when the market is open and the candle is
				 * for the Tradestrategies trading day.
				 */
				if (TradingCalendar.isMarketHours(startPeriod)
						&& TradingCalendar.sameDay(getTradestrategy()
								.getTradingday().getOpen(), startPeriod)) {

					// _log.info(getTradestrategy().getStrategy().getClassName()
					// + " symbol: " + getSymbol() + " startPeriod: "
					// + startPeriod);

					CandleItem prevCandleItem = (CandleItem) candleSeries
							.getDataItem(getCurrentCandleCount() - 1);

					/*
					 * Is it the the 9:35 candle? and we have not created an
					 * open position trade.
					 */
					if (startPeriod.equals(TradingCalendar.getSpecificTime(
							startPeriod, 9, 35)) && newBar) {
						Side side = Side.newInstance(Side.SLD);
						if (prevCandleItem.isSide(Side.BOT)) {
							side = Side.newInstance(Side.BOT);
						}
						Money price = new Money(prevCandleItem.getHigh());
						Money priceStop = new Money(prevCandleItem.getLow());
						String action = Action.BUY;
						if (side.equalsCode(Side.SLD)) {
							price = new Money(prevCandleItem.getLow());
							priceStop = new Money(prevCandleItem.getHigh());
							action = Action.SELL;
						}

						Money priceClose = new Money(prevCandleItem.getClose());
						Entrylimit entrylimit = getEntryLimit().getValue(
								priceClose);

						double highLowRange = Math.abs(prevCandleItem.getHigh()
								- prevCandleItem.getLow());
						// double openCloseRange = Math.abs(prevCandleItem
						// .getOpen() - prevCandleItem.getClose());

						priceStop = new Money(prevCandleItem.getOpen());

						// If the candle less than the entry limit %
						if (((highLowRange) / prevCandleItem.getClose()) < entrylimit
								.getPercent().doubleValue()) {
							// TODO add the tails as a % of the body.
							_log.info(" We have a trade!!  Symbol: "
									+ getSymbol() + " Time: " + startPeriod);
							/*
							 * Create an open position.
							 */
							createRiskOpenPosition(action, price, priceStop,
									true);

						} else {
							_log.info("Rule 9:35 5min bar outside % limits. Symbol: "
									+ getSymbol() + " Time: " + startPeriod);
							updateTradestrategyStatus(TradestrategyStatus.PERCENT);
							// Kill this process we are done!
							this.cancel();
						}

					} else if (startPeriod.equals(TradingCalendar
							.getSpecificTime(startPeriod, 10, 30))
							|| startPeriod.after(TradingCalendar
									.getSpecificTime(startPeriod, 10, 30))) {

						if (!this.isPositionOpen()
								&& !TradestrategyStatus.CANCELLED
										.equals(getTradestrategy().getStatus())) {
							updateTradestrategyStatus(TradestrategyStatus.TO);
							cancelOrder(this.getOpenPositionOrder());
							// No trade we timed out
							_log.info("Rule 10:30:00 bar, time out unfilled open position Symbol: "
									+ getSymbol() + " Time: " + startPeriod);
						}
						this.cancel();
					}
				}
			}

		} catch (Exception ex) {
			_log.error("Error  runRule exception: " + ex.getMessage(), ex);
			error(1, 10, "Error  runRule exception: " + ex.getMessage());
		}
	}
}
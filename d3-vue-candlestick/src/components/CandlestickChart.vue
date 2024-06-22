<template>
  <div>
    <!-- Trading Pair and Timeframe Selection -->
    <div>
      <label for="tradingPair">Select Trading Pair:</label>
      <select v-model="selectedPair" @change="fetchData">
        <option v-for="pair in tradingPairs" :key="pair.id" :value="pair.id">
          {{ pair.display_name }}
        </option>
      </select>

      <label for="timeframe">Select Timeframe:</label>
      <select v-model="selectedTimeframe" @change="fetchData">
        <option v-for="timeframe in timeframes" :key="timeframe.value" :value="timeframe.value">
          {{ timeframe.display_name }}
        </option>
      </select>
    </div>

    <!-- Indicator Selection and Customization -->
    <div>
      <label for="indicatorType">Indicator Type:</label>
      <select v-model="selectedIndicator">
        <option v-for="indicator in indicators" :key="indicator.name" :value="indicator.name">
          {{ indicator.display_name }}
        </option>
      </select>

      <div v-if="selectedIndicator">
        <label for="indicatorPeriod">Period:</label>
        <input type="number" v-model="indicatorParams.period" id="indicatorPeriod" />

        <label for="indicatorColor">Color:</label>
        <input type="color" v-model="indicatorParams.color" id="indicatorColor" />

        <label for="indicatorTimeframe">Timeframe:</label>
        <select v-model="indicatorParams.timeframe" id="indicatorTimeframe">
          <option v-for="timeframe in timeframes" :key="timeframe.value" :value="timeframe.value">
            {{ timeframe.display_name }}
          </option>
        </select>

        <button @click="applyIndicator">Apply</button>
      </div>
    </div>

    <!-- Volume Indicator Toggle -->
    <div>
      <label for="toggleVolume">Show Volume:</label>
      <input type="checkbox" v-model="showVolume" id="toggleVolume" @change="toggleVolume">
    </div>

    <!-- Chart Rendering Area -->
    <div ref="scrollContainer" class="scroll-container">
      <div ref="chart"></div>
    </div>

    <!-- Load More Data Button -->
    <div>
      <button @click="loadMoreData">Load More Data</button>
    </div>

    <!-- Applied Indicators Management -->
    <div>
      <h3>Applied Indicators</h3>
      <ul>
        <li v-for="(indicator, index) in appliedIndicators" :key="index">
          {{ indicator.name }} (Period: {{ indicator.period }}, Timeframe: {{ indicator.timeframe }})
          <button @click="removeIndicator(index)">Remove</button>
        </li>
      </ul>
    </div>
  </div>
</template>

<script>
import * as d3 from 'd3';
import d3Tip from 'd3-tip';
import { SMA, EMA } from 'technicalindicators';

export default {
  name: 'TradingViewChart',
  data() {
    return {
      indicators: [
        { name: 'SMA', display_name: 'Simple Moving Average' },
        { name: 'EMA', display_name: 'Exponential Moving Average' },
      ],
      selectedIndicator: null,
      indicatorParams: {
        period: 14,
        color: '#FF0000',
        timeframe: '3600', // Default indicator timeframe
      },
      appliedIndicators: [],
      showVolume: true,
      data: [],
      x: null,
      y: null,
      svg: null,
      tradingPairs: [],
      selectedPair: 'BTC-USD',
      timeframes: [
        { value: '60', display_name: '1 Minute' },
        { value: '300', display_name: '5 Minutes' },
        { value: '900', display_name: '15 Minutes' },
        { value: '3600', display_name: '1 Hour' },
        { value: '86400', display_name: '1 Day' },
        { value: '604800', display_name: '1 Week' },
      ],
      selectedTimeframe: '86400',
      transformation: {
        k: 1,  // Scale (zoom level)
        x: 0,  // Translation in the x-axis
        y: 0   // Translation in the y-axis
      },
    };
  },
  mounted() {
    this.fetchTradingPairs();
  },
  methods: {
    fetchTradingPairs() {
      fetch('http://localhost:3000/api/pairs')
        .then(response => response.json())
        .then(data => {
          this.tradingPairs = data.pairs;
          this.fetchData();
        });
    },
    fetchData(params = {}) {
      const url = this.buildFetchUrl(params);
      fetch(url)
        .then(response => response.json())
        .then(data => {
          const parseDate = d3.timeParse('%s');
          const parsedData = this.parseData(data['candlestick-data'], parseDate);

          // Clear existing chart elements
          d3.select(this.$refs.chart).selectAll('*').remove();
          
          // Update chart data and re-render
          this.updateChartData(parsedData);
          this.recalculateAndRedrawIndicators(parsedData);
        });
    },
    buildFetchUrl({ start, end } = {}) {
      let url = `http://localhost:3000/api/candlestick/${this.selectedPair}/${this.selectedTimeframe}`;
      if (start && end) {
        url += `?start=${start}&end=${end}`;
      }
      return url;
    },
    removeDuplicatesAndSort(data) {
      const uniqueData = [];
      const dateMap = new Map();

      data.forEach(item => {
        if (!dateMap.has(item.date.getTime())) {
          uniqueData.push(item);
          dateMap.set(item.date.getTime(), true);
        }
      });

      return uniqueData.sort((a, b) => a.date - b.date);
    },
    handleIndicatorApplication() {
      const { period, color, timeframe } = this.indicatorParams;
      const maxIndicatorPeriod = period;
      const firstDate = d3.min(this.data, d => d.date);
      const formattedFirstDate = Math.floor(firstDate.getTime() / 1000) - 1;
      const interval = timeframe;
      const requiredStartDate = formattedFirstDate - (interval * maxIndicatorPeriod);

      this.fetchData({ start: requiredStartDate, end: formattedFirstDate })
        .then(() => {
          this.applyIndicatorLogic(this.selectedIndicator, period, color, timeframe);
        });
    },
    calculateIndicator(Indicator, period) {
      const closingPrices = this.data.map(d => d.close);
      const values = Indicator.calculate({ period, values: closingPrices });
      return this.data.map((d, index) => ({
        date: d.date,
        value: index >= period - 1 ? values[index - (period - 1)] : null
      }));
    },
    applyIndicatorLogic(indicatorName, period, color, timeframe) {
      const Indicator = indicatorName === 'SMA' ? SMA : EMA; // Add more conditions as needed
      const indicatorData = this.calculateIndicator(Indicator, period);

      this.drawIndicator(this.data, indicatorData, indicatorName.toLowerCase(), color, period);
      this.appliedIndicators.push({ name: indicatorName, period, color, timeframe, data: indicatorData });
    },
    updateChartData(newData) {
      // Clear existing data
      this.data = [];

      // Merge and sort data, removing duplicates
      const combinedData = [...newData];
      this.data = this.removeDuplicatesAndSort(combinedData);

      // Create the chart with the updated data
      this.createChart(this.data);
    },
    loadMoreData() {
      const firstDate = d3.min(this.data, d => d.date);
      const formattedFirstDate = Math.floor(firstDate.getTime() / 1000) - 1;
      const interval = this.selectedTimeframe;
      const maxCandles = 300;
      const maxTimeSpan = interval * maxCandles;
      const formattedStartDate = formattedFirstDate - maxTimeSpan;

      console.log('Loading more data...');
      console.log('Formatted Start Date:', formattedStartDate);
      console.log('Formatted First Date:', formattedFirstDate);

      fetch(`http://localhost:3000/api/candlestick/${this.selectedPair}/${this.selectedTimeframe}?start=${formattedStartDate}&end=${formattedFirstDate}`)
        .then(response => {
          if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
          }
          return response.json();
        })
        .then(data => {
          console.log('Data fetched:', data);

          const parseDate = d3.timeParse('%s');
          const newData = this.parseData(data['candlestick-data'], parseDate);

          // Log existing data and new data before merging
          console.log('Existing Data:', this.data);
          console.log('New Data:', newData);

          // Remove duplicates
          const uniqueNewData = newData.filter(newEntry => 
            !this.data.some(existingEntry => existingEntry.date.getTime() === newEntry.date.getTime())
          );

          // Merge and sort data
          this.data = [...uniqueNewData, ...this.data].sort((a, b) => a.date - b.date);

          // Log combined data after merging
          console.log('Combined Data:', this.data);

          // Reapply transformation
          this.createChart(this.data);
          this.reapplyTransformation();
        })
        .catch(error => {
          console.error('Error loading more data:', error);
        });
    },

    reapplyTransformation() {
      const { k, x, y, xDomain, yDomain } = this.transformation;

      console.log('Reapplying transformation:', this.transformation);

      try {
        // Reapply zoom and pan transformations
        this.svg.call(
          d3.zoom().transform,
          d3.zoomIdentity.translate(x, y).scale(k)
        );

        // Apply scales and redraw elements
        this.x.domain(xDomain);
        this.y.domain(yDomain);

        this.svg.select('.x-axis').call(d3.axisBottom(this.x).tickFormat(d3.timeFormat('%Y-%m-%d %H:%M')));
        this.svg.select('.y-axis').call(d3.axisLeft(this.y));

        const candleWidth = Math.max(1, (this.width / this.data.length) * 0.6); // Adjusted width calculation for larger candles

        // Log attribute values before updating elements
        console.log('Candle Attributes Before Update:', this.svg.selectAll('rect.candle').data().map(d => ({
          x: this.x(d.date),
          y: this.y(Math.max(d.open, d.close)),
          height: Math.abs(this.y(d.open) - this.y(d.close)),
          fill: d.open > d.close ? 'red' : 'green'
        })));

        this.svg.selectAll('line.stem')
          .attr('x1', (d) => this.x(d.date))
          .attr('x2', (d) => this.x(d.date))
          .attr('y1', (d) => this.y(d.low))
          .attr('y2', (d) => this.y(d.high));

        this.svg.selectAll('rect.candle')
          .attr('x', (d) => this.x(d.date) - candleWidth / 2)
          .attr('y', (d) => {
            const yVal = this.y(Math.max(d.open, d.close));
            return isNaN(yVal) ? 0 : yVal;
          })
          .attr('height', (d) => {
            const heightVal = Math.abs(this.y(d.open) - this.y(d.close));
            return isNaN(heightVal) ? 0 : heightVal;
          })
          .attr('fill', (d) => d.open > d.close ? 'red' : 'green');

        if (this.showVolume) {
          const volumeScale = d3.scaleLinear()
            .domain([0, d3.max(this.data, (d) => d.volume || 0)])
            .range([this.height, this.height - 100]);

          this.svg.selectAll('rect.volume')
            .attr('x', (d) => this.x(d.date) - candleWidth / 2)
            .attr('y', (d) => {
              const yVal = volumeScale(d.volume);
              return isNaN(yVal) ? this.height : yVal;
            })
            .attr('height', (d) => {
              const heightVal = this.height - volumeScale(d.volume);
              return isNaN(heightVal) ? 0 : heightVal;
            });
        }

        // Log attribute values after updating elements
        console.log('Candle Attributes After Update:', this.svg.selectAll('rect.candle').data().map(d => ({
          x: this.x(d.date),
          y: this.y(Math.max(d.open, d.close)),
          height: Math.abs(this.y(d.open) - this.y(d.close)),
          fill: d.open > d.close ? 'red' : 'green'
        })));

        this.redrawIndicators();
      } catch (error) {
        console.error('Error reapplying transformation:', error);
      }
    },
    recalculateAndRedrawIndicators(newData) {
      // Remove existing indicators
      this.svg.selectAll('.indicator').remove();

      this.appliedIndicators.forEach(indicator => {
        let newIndicatorData;

        switch (indicator.name) {
          case 'SMA':
            // Calculate the SMA for the new data and prepend to existing indicator data
            newIndicatorData = this.calculateSMA(newData, indicator.period);
            break;
          case 'EMA':
            // Calculate the EMA for the new data and prepend to existing indicator data
            newIndicatorData = this.calculateEMA(newData, indicator.period);
            break;
        }

        // Combine new and existing indicator data
        indicator.data = [...newIndicatorData, ...indicator.data].sort((a, b) => a.date - b.date);

        // Draw the updated indicator
        this.drawIndicator(this.data, indicator.data, indicator.name.toLowerCase(), indicator.color, indicator.period);
      });
    },
    createLegend(svg) {
      const legend = svg.append('g')
        .attr('class', 'legend')
        .attr('transform', `translate(10, 10)`); // Move to top-left corner

      legend.append('rect')
        .attr('width', 180)
        .attr('height', 50)
        .attr('fill', 'white')
        .attr('stroke', 'black')
        .attr('stroke-width', 0.5);

      legend.append('text')
        .attr('x', 10)
        .attr('y', 20)
        .text('Price: ')
        .attr('font-size', '12px');

      svg.selectAll('.candle')
        .on('mouseover', function (event, d) {
          legend.select('text')
            .text(`Price: ${d.close}`);
        });
    },
    createChart(data) {
      d3.select(this.$refs.chart).selectAll('*').remove();

      const margin = { top: 20, right: 30, bottom: 30, left: 40 };
      const width = 800 - margin.left - margin.right;
      const height = 400 - margin.top - margin.bottom;

      const svg = d3.select(this.$refs.chart)
        .append('svg')
        .attr('width', width + margin.left + margin.right)
        .attr('height', height + margin.top + margin.bottom)
        .append('g')
        .attr('transform', `translate(${margin.left},${margin.top})`);

      const { x, y, volumeScale } = this.createScales(data, width, height);

      this.createAxes(svg, x, y, height);

      const zoom = this.createZoom(svg, x, y, width, height, data);
      svg.append('rect')
        .attr('width', width)
        .attr('height', height)
        .style('fill', 'none')
        .style('pointer-events', 'all')
        .attr('transform', 'translate(0,0)')
        .call(zoom);

      const tip = this.createTooltip();
      svg.call(tip);

      this.drawCandlesticks(svg, data, x, y, width, tip);
      if (this.showVolume) {
        this.drawVolumes(svg, data, x, volumeScale, height, width);
      }

      this.x = x;
      this.y = y;
      this.svg = svg;

      this.createLegend(svg);
      this.redrawIndicators();
    },

    parseData(data, parseDate) {
      return data.map(d => ({
        date: parseDate(d[0]),
        low: d[1],
        high: d[2],
        open: d[3],
        close: d[4],
        volume: d[5] || 0,
      })).sort((a, b) => a.date - b.date);
    },
    createScales(data, width, height) {
      const x = d3.scaleTime()
        .domain(d3.extent(data, (d) => d.date))
        .range([0, width]);

      const y = d3.scaleLinear()
        .domain([d3.min(data, (d) => d.low), d3.max(data, (d) => d.high)])
        .range([height, 0]);

      const volumeScale = d3.scaleLinear()
        .domain([0, d3.max(data, (d) => d.volume || 0)])
        .range([height, height - 100]);

      return { x, y, volumeScale };
    },
    createAxes(svg, x, y, height) {
      svg.append('g')
        .attr('class', 'x-axis')
        .attr('transform', `translate(0,${height})`)
        .call(d3.axisBottom(x).tickFormat(d3.timeFormat('%Y-%m-%d %H:%M')));

      svg.append('g')
        .attr('class', 'y-axis')
        .call(d3.axisLeft(y));
    },
    createZoom(svg, x, y, width, height, data) {
      const candleWidth = Math.max(1, (width / data.length) * 0.6); // Adjusted width calculation for larger candles

      const zoomed = (event) => {
        const transform = event.transform;
        const newX = transform.rescaleX(x);
        const newY = transform.rescaleY(y); // Rescale y-axis

        // Update transformation object
        this.transformation = {
          k: transform.k,
          x: transform.x,
          y: transform.y,
          xDomain: newX.domain(),
          yDomain: newY.domain(),
          width: width,
          height: height,
          viewbox: svg.attr('viewBox'),
          lastInteraction: new Date().toISOString(),
          zoomExtent: [1, 10],
          panExtent: [[-Infinity, -Infinity], [Infinity, Infinity]], // Example pan extent
          initialXDomain: x.domain(),
          initialYDomain: y.domain(),
          currentViewport: {
            width: width,
            height: height,
            aspectRatio: width / height,
          }
        };

        console.log('Current Transformation:', this.transformation);

        svg.select('.x-axis').call(d3.axisBottom(newX).tickFormat(d3.timeFormat('%Y-%m-%d %H:%M')));
        svg.select('.y-axis').call(d3.axisLeft(newY)); // Update y-axis

        svg.selectAll('line.stem')
          .attr('x1', (d) => newX(d.date))
          .attr('x2', (d) => newX(d.date))
          .attr('y1', (d) => newY(d.low))
          .attr('y2', (d) => newY(d.high));

        svg.selectAll('rect.candle')
          .attr('x', (d) => newX(d.date) - candleWidth / 2)
          .attr('y', (d) => newY(Math.max(d.open, d.close)))
          .attr('height', (d) => Math.abs(newY(d.open) - newY(d.close)))
          .attr('fill', (d) => d.open > d.close ? 'red' : 'green');

        svg.selectAll('rect.volume')
          .attr('x', (d) => newX(d.date) - candleWidth / 2);
      };

      return d3.zoom()
        .scaleExtent([1, 10])
        .translateExtent([[-Infinity, -Infinity], [Infinity, Infinity]]) // Allow infinite panning
        .extent([[0, 0], [width, height]])
        .on('zoom', zoomed);
    },
    createTooltip() {
      return d3Tip()
        .attr('class', 'd3-tip')
        .offset([-10, 0])
        .html((d) => `
          <strong>Date:</strong> <span>${d3.timeFormat('%Y-%m-%d %H:%M')(d.date)}</span><br>
          <strong>Open:</strong> <span>${d.open}</span><br>
          <strong>High:</strong> <span>${d.high}</span><br>
          <strong>Low:</strong> <span>${d.low}</span><br>
          <strong>Close:</strong> <span>${d.close}</span>
        `);
    },
    drawCandlesticks(svg, data, x, y, width, tip) {
      const candleWidth = Math.max(1, (width / data.length) * 0.6); // Adjusted width calculation for larger candles

      const candlesticks = svg.selectAll('g.candlestick')
        .data(data)
        .enter()
        .append('g')
        .attr('class', 'candlestick')
        .on('mouseover', (event, d) => tip.show(d, event.target))
        .on('mouseout', tip.hide);

      candlesticks.append('line')
        .attr('class', 'stem')
        .attr('x1', (d) => x(d.date))
        .attr('x2', (d) => x(d.date))
        .attr('y1', (d) => y(d.low))
        .attr('y2', (d) => y(d.high))
        .attr('stroke', 'black');

      candlesticks.append('rect')
        .attr('class', 'candle')
        .attr('x', (d) => x(d.date) - candleWidth / 2)
        .attr('y', (d) => y(Math.max(d.open, d.close)))
        .attr('width', candleWidth)
        .attr('height', (d) => Math.abs(y(d.open) - y(d.close)))
        .attr('fill', (d) => d.open > d.close ? 'red' : 'green')
        .attr('stroke', (d) => d.open > d.close ? 'red' : 'green');
    },
    drawVolumes(svg, data, x, volumeScale, height, width) {
      svg.selectAll('rect.volume')
        .data(data)
        .enter().append('rect')
        .attr('class', 'volume')
        .attr('x', (d) => x(d.date) - width / data.length / 2)
        .attr('y', (d) => volumeScale(d.volume))
        .attr('width', width / data.length)
        .attr('height', (d) => height - volumeScale(d.volume))
        .attr('fill', (d) => (d.open > d.close ? 'red' : 'green'))
        .attr('opacity', 0.3);
    },
    drawIndicator(data, indicatorData, indicatorClass, color, period) {
      const line = d3.line()
        .defined(d => d.value !== null)
        .x(d => this.x(d.date))
        .y(d => this.y(d.value));

      const filteredData = indicatorData.filter(d => d.value !== null);

      this.svg.append('path')
        .datum(filteredData)
        .attr('class', `indicator ${indicatorClass}-${period}`)
        .attr('d', line)
        .attr('stroke', color)
        .attr('stroke-width', 1.5)
        .attr('fill', 'none');
    },
    applyIndicator() {
      if (this.selectedIndicator) {
        const { period, color, timeframe } = this.indicatorParams;
        const maxIndicatorPeriod = period;
        const firstDate = d3.min(this.data, d => d.date);
        const formattedFirstDate = Math.floor(firstDate.getTime() / 1000) - 1;
        const interval = timeframe;
        const requiredStartDate = formattedFirstDate - (interval * maxIndicatorPeriod);

        this.fetchAdditionalData(requiredStartDate, formattedFirstDate, timeframe)
          .then(() => {
            let indicatorData;

            switch (this.selectedIndicator) {
              case 'SMA':
                indicatorData = this.calculateSMA(this.data, period);
                this.drawIndicator(this.data, indicatorData, 'sma', color, period);
                break;
              case 'EMA':
                indicatorData = this.calculateEMA(this.data, period);
                this.drawIndicator(this.data, indicatorData, 'ema', color, period);
                break;
            }

            this.appliedIndicators.push({ name: this.selectedIndicator, period, color, timeframe, data: indicatorData });
          })
          .catch(error => {
            console.error('Error fetching additional data:', error);
          });
      }
    },
    fetchAdditionalData(start, end, timeframe) {
      return new Promise((resolve, reject) => {
        const url = `http://localhost:3000/api/candlestick/${this.selectedPair}/${timeframe}?start=${start}&end=${end}`;

        fetch(url)
          .then(response => {
            if (!response.ok) {
              throw new Error(`HTTP error! status: ${response.status}`);
            }
            return response.json();
          })
          .then(data => {
            const parseDate = d3.timeParse('%s');
            const newData = this.parseData(data['candlestick-data'], parseDate);
            this.data = [...newData, ...this.data];
            resolve();
          })
          .catch(error => {
            console.error('Error fetching additional data:', error);
            reject(error);
          });
      });
    },
    calculateSMA(data, period) {
      const closingPrices = data.map(d => d.close);
      const smaValues = SMA.calculate({ period, values: closingPrices });
      return data.map((d, index) => ({
        date: d.date,
        value: index >= period - 1 ? smaValues[index - (period - 1)] : null
      }));
    },
    calculateEMA(data, period) {
      const closingPrices = data.map(d => d.close);
      const emaValues = EMA.calculate({ period, values: closingPrices });
      return data.map((d, index) => ({
        date: d.date,
        value: index >= period - 1 ? emaValues[index - (period - 1)] : null
      }));
    },
    removeIndicator(index) {
      const indicator = this.appliedIndicators[index];
      d3.select(this.$refs.chart).select(`.${indicator.name.toLowerCase()}-${indicator.period}`).remove();
      this.appliedIndicators.splice(index, 1);
      this.redrawIndicators();
    },
    redrawIndicators() {
      if (!this.svg) return;

      this.svg.selectAll('.indicator').remove();

      this.appliedIndicators.forEach(indicator => {
        this.drawIndicator(this.data, indicator.data, indicator.name.toLowerCase(), indicator.color, indicator.period);
      });
    },
    toggleVolume() {
      this.createChart(this.data);
    },
  },
  beforeUnmount() {
    this.$refs.scrollContainer.removeEventListener('scroll', this.handleScroll);
  },
};
</script>

<style scoped>
.scroll-container {
  max-height: 600px;
  overflow-x: scroll;
  white-space: nowrap;
}
.stem {
  stroke: black;
}
.candle {
  stroke: black;
}
.d3-tip {
  line-height: 1;
  font-weight: bold;
  padding: 12px;
  background: rgba(0, 0, 0, 0.8);
  color: #fff;
  border-radius: 2px;
}
.d3-tip:after {
  box-sizing: border-box;
  display: inline;
  font-size: 10px;
  width: 100%;
  line-height: 1;
  color: rgba(0, 0, 0, 0.8);
  content: '\25BC';
  position: absolute;
  text-align: center;
}
.d3-tip.n:after {
  margin: -1px 0 0 0;
  top: 100%;
  left: 0;
}
</style>

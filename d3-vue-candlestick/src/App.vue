<template>
  <div id="app">
    <CandlestickChart />
  </div>
</template>

<script>
import CandlestickChart from './components/CandlestickChart.vue';

export default {
  name: 'App',
  components: {
    CandlestickChart
  }
};
</script>

<style>
#app {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-align: center;
  color: #2c3e50;
  margin-top: 60px;
}
</style>

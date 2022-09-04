var processVariables = undefined
var idCounter = 0;
var chart = undefined;

const supportedExtras = function (name) {
    return [`@${name}.array-index`]
}

function fetchSdVariables(processId) {
    return fetch(`/api/${processId}/sd-variables`)
        .then((response) => response.json())
        .then((data) => processVariables = data)
        .catch((err) => console.error(`Failed fetching: ${err}`));
}

function autocompleteSdVar(processId, elementId) {
    const list = document.getElementById(elementId);
    function fillData() {
        list.replaceChildren();
        processVariables.forEach(item => {
            const toAdd = [item, ...supportedExtras(item)]
            toAdd.forEach(value => {
                let option = document.createElement('option');
                option.value = value;
                list.appendChild(option);
            })
        })
    }

    if (!processVariables) {
        fetchSdVariables(processId).then(_ => fillData())
    } else {
        fillData()
    }
}

function autocompleteSavedPlots(listElementId) {
    const list = document.getElementById(listElementId);

    list.replaceChildren();
    readConfig().forEach((config) => {
        let option = document.createElement('option');
        option.value = config.join("; ");
        list.appendChild(option);
    })
}

function savedPlotSelected(inputId, listId, processId, chartsListId, dataPointIdxElemId, useLatest) {
    const val = document.getElementById(inputId).value;
    const opts = document.getElementById(listId).childNodes;
    const chartList = document.getElementById(chartsListId);

    for (let i = 0; i < opts.length; i++) {
        if (opts[i].value === val) {
            chartList.replaceChildren();
            val.split("; ").forEach((chartDef) => {
                const def = chartDef.split(",")
                addPlotToList(def[0], def[1], processId, chartsListId, dataPointIdxElemId, useLatest)
            });
            updateChart(processId, chartsListId, dataPointIdxElemId, useLatest)
            break;
        }
    }
}

function addPlotToList(plotXval, plotYval, processId, chartsListId, dataPointIdx, useLatest) {
    let option = document.createElement('li');
    let list = document.getElementById(chartsListId);
    option.plotId = `${plotXval} --- ${plotYval}`;
    option.id = `option-${idCounter++}`;
    let span = document.createElement('span');
    span.innerHTML = `${option.plotId} <a href="#" onclick="removePlot('${processId}', '${chartsListId}', '${option.id}', '${dataPointIdx}', '${useLatest}')">Delete</a>`
    option.appendChild(span);
    list.appendChild(option);
}

function addPlot(processId, chartsListId, plotXname, plotYname, dataPointIdxElemId, useLatest) {
    let plotXval = document.getElementsByName(plotXname)[0].value
    let plotYval = document.getElementsByName(plotYname)[0].value
    addPlotToList(plotXval, plotYval, processId, chartsListId, dataPointIdxElemId, useLatest);
    updateChart(processId, chartsListId, dataPointIdxElemId, useLatest)
}

function removePlot(processId, chartsListId, optionId, dataPointIdx, useLatest) {
    document.getElementById(chartsListId).removeChild(document.getElementById(optionId))
    updateChart(processId, chartsListId, dataPointIdx, useLatest)
}

function updateChartPointIdx(processId, chartsListId, dataPointIdxElemId, useLatest, updateType) {
    updatedValue = parseInt(document.getElementById(dataPointIdxElemId).value)
    const dsSize = parseInt(dataSize)
    switch (updateType) {
        case 'next':
            if (updatedValue + 1 > dsSize) {
                return
            }
            updatedValue++
            break;
        case 'prev':
            if (updatedValue - 1 < 1) {
                return
            }
            updatedValue--;
            break;
        case 'random':
            updatedValue = Math.floor(Math.random() * (dsSize + 1))
            break;
        case 'blur':
            break;
        default:
            throw Error(`Unknown updateType ${updateType}`)
    }
    document.getElementById(dataPointIdxElemId).value = updatedValue
    updateChart(processId, chartsListId, dataPointIdxElemId, useLatest)
}

function extractPlots(list) {
    const pairs = []
    list.childNodes.forEach((node) => {
        if (node.tagName === "li" || node.tagName === "LI") {
            const split = node.plotId.split(" --- ")
            pairs.push([split[0], split[1]])
        }
    });
    return pairs;
}

function updateChart(processId, chartsListId, dataPointIdxElemId, useLatest) {
    const list = document.getElementById(chartsListId);
    const pairs = extractPlots(list);
    fetchAndPlot(processId, pairs, document.getElementById(dataPointIdxElemId).value, useLatest)
}

function readConfig() {
    const result = JSON.parse(window.localStorage.getItem("__plot-config"))
    if (!result) {
        return []
    }
    return result
}

function rememberPlotConfig(chartsListId) {
    const list = document.getElementById(chartsListId);
    const pairs = extractPlots(list);
    const cfg = readConfig()
    if (cfg.length === 3) {
        cfg.pop()
    }
    cfg.unshift(pairs)
    window.localStorage.setItem("__plot-config", JSON.stringify(cfg))
}

function fetchAndPlot(processId, variablePairs, trainingEntryIdx, useLatest) {
    document.getElementById("loader").classList.remove("hide-loader")
    let fetchableVars = variablePairs.flatMap(it => it).filter(it => !it.startsWith("@"))
    let syntheticVars = variablePairs.flatMap(it => it).filter(it => it.startsWith("@"))

    let isLatest = document.getElementById(useLatest).checked
    if (0 === fetchableVars.length) {
        drawChart([], [])
        return
    }

    fetch(`/api/${processId}/sd-variables/output/training?variables=${fetchableVars}&trainingEntryIdx=${trainingEntryIdx}&latest=${isLatest}`)
        .then((response) => response.json())
        .then((response) => enhanceResponseWithSyntheticVars(response, syntheticVars))
        .then((data) => drawChart(data, variablePairs))
        .catch((err) => console.error(`Failed fetching: ${err}`));
}

function drawChart(data, chartDataPairs) { // data is Map<String, FloatArray>
    document.getElementById("loader").classList.add("hide-loader")
    chart = echarts.init(document.getElementById('chart'))
    chart.clear()
    if (chartDataPairs.length === 0) {
        return
    }

    let colors = ["#e60049", "#0bb4ff", "#50e991", "#e6d800", "#9b19f5", "#ffa300", "#dc0ab4", "#b3d4ff", "#00bfa0"];

    let xAxesUniq = new Set();
    chartDataPairs.map(pair => xAxesUniq.add(pair[0]));
    let xAxes = [...xAxesUniq]
    let option = {
        color: colors,
        tooltip: {
            trigger: 'none',
            axisPointer: {
                type: 'cross'
            }
        },
        legend: {},
        grid: {
            top: 70,
            bottom: 50
        },
        xAxis: xAxes.map((xAxis, index) => xAxisTemplate(xAxis, data[xAxis], colors[index % colors.length])),
        yAxis: [
            {
                type: 'value'
            }
        ],
        dataZoom: [
            {
                type: 'slider'
            },
            {
                type: 'inside'
            }
        ],
        series: chartDataPairs.map((pair, index) => yAxisTemplate(pair[1], data[pair[1]], xAxes.indexOf(pair[0]), colors[index % colors.length]))
    };
    chart.setOption(option);
}

function xAxisTemplate(name, data, color) {
    return {
        type: 'category',
        axisTick: {
            alignWithLabel: true
        },
        axisLine: {
            onZero: false,
            lineStyle: {
                color: color
            }
        },
        axisPointer: {
            label: {
                formatter: function (params) {
                    return `${name}  ${params.value} ${(params.seriesData.length ? '：' + params.seriesData[0].data : '')}`;
                }
            }
        },

        data: data
    }
}

function yAxisTemplate(name, data, xAxisInd, color) {
    return {
        name: name,
        type: 'line',
        xAxisIndex: xAxisInd,
        smooth: true,
        color: color,
        emphasis: {
            focus: 'series'
        },
        data: data
    }
}

function enhanceResponseWithSyntheticVars(response, syntheticVars) {
    for (let synthVar of syntheticVars) {
        let splitVal = synthVar.split("\.")
        let originName = splitVal[0].substring(1)
        let operation = splitVal[1]
        if (!response[originName]) {
            continue
        }

        switch (operation) {
            case "array-index":
                response[synthVar] = Array(response[originName].length).fill().map((element, index) => index)
                break
            default:
                throw Error(`Unknown operation ${operation} of ${synthVar}`)
        }
    }
    return response
}
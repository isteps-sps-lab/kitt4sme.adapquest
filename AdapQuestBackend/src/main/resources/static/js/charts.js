// code adapted from https://www.d3-graph-gallery.com/ and https://observablehq.com/@bjedwards/multi-line-chart-d3-v6

const colors = ['#e41a1c', '#377eb8', '#4daf4a', '#984ea3', '#ff7f00', '#ffff33', '#a65628', '#f781bf', '#999999']

const margin = {top: 20, right: 30, bottom: 60, left: 60};
const width = 500 - margin.left - margin.right;
const height = 400 - margin.top - margin.bottom;

// following is the entropy lines chart
d3.json('/survey/states/' + token)
    .then((data) => {
        const entropies = []

        data.sort((a, b) => a.totalAnswers - b.totalAnswers)
            .forEach(d => {
                d.skills.forEach(s => {
                    entropies.push({'answers': d.totalAnswers, 'skill': s.name, 'value': d.scoreDistribution[s.name]})
                });
            });
        return entropies;
    })
    .then(data => {
        // group the data: I want to draw one line per group
        const groups = d3.group(data, d => d.skill);

        const svg = d3.select('#chart-entropy')
            .append('svg')
            .attr('width', width + margin.left + margin.right)
            .attr('height', height + margin.top + margin.bottom)
            .append('g')
            .attr('transform', `translate(${margin.left}, ${margin.top})`);

        const ticks = d3.max([1, d3.max(data, d => d.answers)]);
        const x = d3.scaleLinear()
            .domain([0, ticks])
            .range([0, width]);
        svg.append('g')
            .attr('transform', `translate(0, ${height})`)
            .call(d3.axisBottom(x).ticks(ticks));

        const y = d3.scaleLinear()
            .domain([0, 1])
            .range([height, 0]);
        svg.append('g')
            .call(d3.axisLeft(y));

        const keys = Array.from(groups.keys());
        const color = d3.scaleOrdinal()
            .domain(keys)
            .range(colors.slice(0, keys.length));

        svg.append('path')
            .data(groups)
            .join('path')
            .attr('class', 'chart-line')
            .attr('stroke', d => color(d[0]))
            .attr('d', d => {
                return d3.line()
                    .x(d => x(d.answers))
                    .y(d => y(d.value))
                    (d[1])
            });

        // This allows to find the closest X index of the mouse:
        const bisect = d3.bisector(d => d.answers).left;

        // Create the circle that travels along the curve of chart
        const focus = svg
            .append('g')
            .append('circle')
            .attr('class', 'chart-circle')
            .attr('r', 8.5);

        const tooltip = d3.select('#chart-entropy').append('div').attr('class', 'bar-tooltip');

        const mouseOver = () => {
            focus.style('opacity', 1);
            tooltip.style('opacity', 1);
        };
        const mouseMove = (event) => {
            const [ex, ey] = d3.pointer(event)
            const values = groups.get(keys[0]);
            const x0 = x.invert(ex);
            const i = bisect(values, x0, 1);
            const d = values[i];
            focus
                .attr('cx', x(d.answers))
                .attr('cy', y(d.value));
            tooltip
                .html('H=' + d.value)
                .style('left', (event.x) + 'px')
                .style('top', (event.y + 20) + 'px');
        }
        const mouseLeave = () => {
            focus.style('opacity', 0);
            tooltip.style('opacity', 0);
        };

        svg
            .append('rect')
            .style('fill', 'none')
            .style('pointer-events', 'all')
            .attr('width', width)
            .attr('height', height)
            .on('mouseover', mouseOver)
            .on('mousemove', mouseMove)
            .on('mouseleave', mouseLeave);
    });

// following is the distribution bar chart
d3.json('/survey/state/' + token)
    .then(data => {
        const distributions = []
        data.skills.forEach(s => {
            s.states.forEach(st => {
                distributions.push({
                    'name': `P(${s.name}=${st.name})`,
                    'value': data.skillDistribution[s.name][st.state]
                })
            });
        });
        return distributions;
    })
    .then((data) => {
        const svg = d3.select('#chart-distribution')
            .append('svg')
            .attr('width', width + margin.left + margin.right)
            .attr('height', height + margin.top + margin.bottom)
            .append('g')
            .attr('transform', `translate(${margin.left}, ${margin.top})`);

        const tooltip = d3.select('#chart-distribution').append('div').attr('class', 'bar-tooltip');

        const x = d3.scaleBand()
            .range([0, width])
            .domain(data.map(d => d.name))
            .padding(0.2);
        svg.append('g')
            .attr('transform', `translate(0, ${height})`)
            .call(d3.axisBottom(x))
            .selectAll('text')
            .attr('transform', 'translate(-10,0)rotate(-35)')
            .style('text-anchor', 'end');

        const y = d3.scaleLinear()
            .domain([0, 1])
            .range([height, 0]);
        svg.append('g')
            .call(d3.axisLeft(y));

        const mouseOver = (event, d) => {
            const key = d.name;
            const value = d.value;
            tooltip.html(`${key} = ${value}`).style('opacity', 1);
        }
        const mouseMove = (event, d) => {
            tooltip
                .style('left', (event.x) + 'px')
                .style('top', (event.y + 20) + 'px');
        }
        let mouseLeave = (event, d) => {
            tooltip.style('opacity', 0);
        }

        svg.selectAll('mybar')
            .data(data)
            .enter()
            .append('rect')
            .attr('x', d => x(d.name))
            .attr('y', d => y(d.value))
            .attr('width', x.bandwidth())
            .attr('height', d => height - y(d.value))
            .attr('fill', '#69b3a2')
            .on('mouseover', mouseOver)
            .on('mousemove', mouseMove)
            .on('mouseleave', mouseLeave);
    });
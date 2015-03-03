LineAnnotate = Rickshaw.Class.create({

  initialize: function(args) {
    var graph = this.graph = args.graph;

    this.yFormatter = args.yFormatter || function(y) {
      return y === null ? y : y.toFixed(2);
    };

    var element = this.element = document.createElement('div');
    element.className = 'detail';

    this.visible = true;
    graph.element.appendChild(element);

    this.lastEvent = null;
    this._addListeners();

    this.onShow = args.onShow;
    this.onHide = args.onHide;
    this.onRender = args.onRender;

    this.formatter = args.formatter || this.formatter;
  },

  formatter: function(series, x, y, formattedY, d) {
    return series.name + ':&nbsp;' + formattedY;
  },

  update: function() {
    var graph = this.graph;

    var j = 0;
    var points = [];

    this.graph.series.active().forEach(function(series) {
      var data = graph.stackedData[j++];

      data.forEach(function(value){
        var yFormatter = series.yFormatter || this.yFormatter;

        var point = {
          formattedYValue: yFormatter(series.scale ? series.scale.invert(value.y) : value.y),
          series: series,
          value: value,
          distance: 0,
          order: j,
          name: series.name
        };

        points.push(point);
      }, this);
    }, this);

    this.element.innerHTML = '';
    this.element.style.left = '0px';

    this.visible && this.render(points);
  },

  hide: function() {
    this.visible = false;
    this.element.classList.add('inactive');

    if (typeof this.onHide == 'function') {
      this.onHide();
    }
  },

  show: function() {
    this.visible = true;
    this.element.classList.remove('inactive');

    if (typeof this.onShow == 'function') {
      this.onShow();
    }
  },

  render: function(points) {
    var graph = this.graph;
    var alignables = [];
    this.element.innerHTML = '';
    points.forEach(function(point){
      if (point.value.y === null) return;

      var formattedYValue = point.formattedYValue;

      var outer = document.createElement('div');
      outer.className = 'line_annotation_outer';
      outer.style.left = graph.x(point.value.x) + 'px';
      outer.style.top = graph.y(point.value.y0 + point.value.y) + 'px';

      var item = document.createElement('div');
      item.className = 'line_annotation';

      // invert the scale if this series displays using a scale
      var series = point.series;
      var actualY = series.scale ? series.scale.invert(point.value.y) : point.value.y;

      item.innerHTML = this.formatter(series, point.value.x, actualY, formattedYValue, point);

      outer.appendChild(item);
      this.element.appendChild(outer);

      item.classList.add('active');

      // Assume left alignment until the element has been displayed and
      // bounding box calculations are possible.
      alignables.push(item);
    }, this);

    alignables.forEach(function(el) {
      el.classList.add('left');
    });

    this.show();

    // If left-alignment results in any error, try right-alignment.
    alignables.forEach(function(el) {
      var leftAlignError = this._calcLayoutError(el);
      if (leftAlignError > 0) {
        el.classList.remove('left');
        el.classList.add('right');

        // If right-alignment is worse than left alignment, switch back.
        var rightAlignError = this._calcLayoutError(el);
        if (rightAlignError > leftAlignError) {
          el.classList.remove('right');
          el.classList.add('left');
        }
      }
    }, this);

    if (typeof this.onRender == 'function') {
      this.onRender(points);
    }
  },

  _calcLayoutError: function(el) {
    // Layout error is calculated as the number of linear pixels by which
    // an alignable extends past the left or right edge of the parent.
    var parentRect = this.element.parentNode.getBoundingClientRect();

    var error = 0;
    var rect = el.getBoundingClientRect();
    if (!rect.width) {
      return;
    }

    if (rect.right > parentRect.right) {
      error += rect.right - parentRect.right;
    }

    if (rect.left < parentRect.left) {
      error += parentRect.left - rect.left;
    }
    return error;
  },

  _addListeners: function() {
    this.graph.onUpdate( function() { this.update() }.bind(this) );
  }
});

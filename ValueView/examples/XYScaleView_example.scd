v = XYScaleView(bounds: Size(300, 300).asRect, specs: [[4,7].asSpec, [26, 35].asSpec], initVals: [6,22]).front
v.front
v.layers
v.specs[0].minval
v.range.strokeWidth = 14
v.specAt_(1, [2,3].asSpec)
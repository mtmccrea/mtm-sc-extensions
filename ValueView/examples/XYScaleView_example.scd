v = XYScaleView(bounds: Size(300, 300).asRect, specs: [[4,7].asSpec, [26, 35].asSpec], initVals: [6,32]).front
v.range.strokeWidth = 0.017
v.specAt_(1, [2,3].asSpec)
v.action = {|v, values, inputs| postf("values: %, inputs: %", values, inputs)}
v.values
v.fixSquare = false
v.moveRelative = true
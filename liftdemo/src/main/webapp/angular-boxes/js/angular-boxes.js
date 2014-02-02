var angularBoxes = angular.module('angularBoxes', []);

angularBoxes.filter('inOutFilter', [function(){
    return function(input, param){
        var ret = [];
        if(!param) param = 'either';
        angular.forEach(input, function(v){
            if(angular.isDefined(v.in) && ((param === 'either') | (param==='true' &&  v.in) | (param==='false' && !v.in))){
                ret.push(v);
            }
        });
        return ret;
    };
}]);

angularBoxes.filter('fromDateFilter', [function(){
    return function(input, param){
        var ret = [];
        if(!param) return input;
        var date = new Date(param).setHours(0,0,0,0)	//For now strip any time, Angular-UI sometimes sets a time
        angular.forEach(input, function(v){
        	console.log(param + ", " + date + ", " + v.time)
            if(angular.isDefined(v.time) && (v.time >= date)){
                ret.push(v);
            }
        });
        return ret;
    };
}]);

angularBoxes.filter('toDateFilter', [function(){
    return function(input, param){
        var ret = [];
        if(!param) return input;
        var date = new Date(param).setHours(0,0,0,0)	//For now strip any time, Angular-UI sometimes sets a time
        angular.forEach(input, function(v){
            if(angular.isDefined(v.time) && (v.time <= date+24*60*60*1000)){
                ret.push(v);
            }
        });
        return ret;
    };
}]);
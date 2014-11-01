window.PolymerBoxes = {
  responseTable: {nextGUID: 0},
  
  handleObserve: function (element, variable, oldValue, newValue) {
	//Make server data in element if we don't have it
	element.boxesServerData = element.boxesServerData || {};

    //Check that we have data, and we aren't doing this in response to a new value from the server
    //We do this by tracking the last index seen
    sd = element.boxesServerData[variable]
    if (sd) {
      if (sd.guid && sd.lastIndexSeen == sd.index) {
        console.log(oldValue + "->" + newValue)
        cmd = sd.guid + '=' + JSON.stringify({"value": newValue, "index": sd.index, "guid": sd.guid})
        console.log(cmd)
        liftAjax.lift_ajaxHandler(cmd, null, null, null)
      }
      sd.lastIndexSeen = sd.index;
    }
  },
  
  server: function(element, variable, data) {
	//Receiving a new action GUID
	if (data.actionGUID) {
	  console.log("GUID, " + variable + " = " + data.actionGUID);
	  element[variable] = data.actionGUID;
	  
	//Receiving a response to a previous action submit
	} else if (data.responseGUID) {
	  console.log("responseGUID, " + variable + " = " + JSON.stringify(data));
	  if (this.responseTable[data.responseGUID]) {
		  this.responseTable[data.responseGUID](data.response);
        delete this.responseTable[data.responseGUID];
        console.log(Object.keys(this.responseTable).length);
	  }
	  
	//Receiving a new data state
	} else {		
	  console.log("Data, " + variable + " = " + JSON.stringify(data));
	  
	  //Make server data in element if we don't have it
	  element.boxesServerData = element.boxesServerData || {};

      //Note we set data (including index) before value, so that textFromBrowser will realise there is new server data when it is called
	  element.boxesServerData[variable] = data;
      element[variable] = data.value;
	}
  },
  
  send: function(guid, data) {
    var json = JSON.stringify(data);
	if (guid) {
      console.log(guid + '=' + json)
      liftAjax.lift_ajaxHandler(guid + '=' + json, null, null, null)
	} else {
	  console.log("Attempting to send " + json + " to guid " + guid);
	}
  },

  transact: function(guid, data, receiver) {
	var responseGUID = "rguid" + this.responseTable.nextGUID;
	this.responseTable.nextGUID++;
	this.responseTable[responseGUID] = receiver;
	
	var dataAndResponse = {'data': data, 'responseGUID': responseGUID}
    var json = JSON.stringify(dataAndResponse);
	if (guid) {
      console.log(guid + '=' + json)
      liftAjax.lift_ajaxHandler(guid + '=' + json, null, null, null)
	} else {
	  console.log("Attempting to send " + json + " to guid " + guid);
	}
  }

  
}

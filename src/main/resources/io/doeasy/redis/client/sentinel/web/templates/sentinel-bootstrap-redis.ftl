<#-- @ftlvariable name="" type="io.doeasy.redis.sentinel.web.SentinelServerView" -->
<!DOCTYPE html>
<html lang="en">
	<head>
		<meta charset="utf-8">
	    <meta http-equiv="X-UA-Compatible" content="IE=edge">
	    <meta name="viewport" content="width=device-width, initial-scale=1">
	    <meta name="description" content="Redis sentinel HA and R/W split client dashboard">
	    <meta name="author" content="kris02.wang">
	    <title>Redis sentinel HA and R/W split client dashboard</title>
	    <link href="/static/bootstrap.css" rel="stylesheet">
	    <link href="/static/bootstrap1.css" rel="stylesheet">
	    <link href="/static/bootstrap2.css" rel="stylesheet">
	    <link href="/static/bootstrap3.css" rel="stylesheet">
	    <link href="/static/bootstrap4.css" rel="stylesheet">
	    <link href="/static/dashboard.css" rel="stylesheet">
	</head>
	<body>
		<nav class="navbar navbar-inverse navbar-fixed-top">
			<div class="container-fluid">
		        <div class="navbar-header">
		          <button type="button" class="navbar-toggle collapsed" data-toggle="collapse" data-target="#navbar" aria-expanded="false" aria-controls="navbar">
		            <span class="sr-only">Toggle navigation</span>
		            <span class="icon-bar"></span>
		            <span class="icon-bar"></span>
		            <span class="icon-bar"></span>
		          </button>
		          <a class="navbar-brand" href="#"><strong>Redis Client</strong></a>
		        </div>
		        <div id="navbar" class="navbar-collapse collapse">
		          <ul class="nav navbar-nav navbar-right">
		            <li><a href="#">Dashboard</a></li>
		            <li><a href="#">Help</a></li>
		          </ul>
		          <form class="navbar-form navbar-right">
		            <input type="text" class="form-control" placeholder="Search...">
		          </form>
		    	</div>
			</div>
		</nav>
		<div class="container-fluid">
			<div class="row">
				<div class="col-sm-3 col-md-2 sidebar">
					<ul class="nav nav-sidebar">
						<li><a href="/sentinel">Sentienl Servers</a></li>
						<li class="active"><a href="/redis?master-name=mymaster">Redis Servers<span class="sr-only">(current)</span></a></li>
					</ul>
				</div>
				<div class="col-sm-9 col-sm-offset-3 col-md-10 col-md-offset-2 main">
					<h3 class="page-header">Master Redis Server Information</h3>
					<div class="table-responsive">
						<table class="table table-striped">
							<thead>
				            	<tr>
				            		<th>#</th>
				                    <th>Ip</th>
				                    <th>Port</th>
				                    <th>timeout</th>
				                    <th>database</th>
				                    <th></th>
				                </tr>
				            </thead>
				            <tbody>
				            	<tr>
				            		<td></td>
									<td>${master.host}</td>
									<td>${master.port}</td>
									<td>${master.timeout}</td>
									<td>${master.database}</td>
									<td><a href="#">detail</a></td>
				            	</tr>
				            </tbody>
						</table>
					</div>
				</div>
				
				<div class="col-sm-9 col-sm-offset-3 col-md-10 col-md-offset-2 main">
					<h3 class="page-header">Slave Redis Servers Information</h3>
					<div class="table-responsive">
						<table class="table table-striped">
							<thead>
				            	<tr>
				            		<th>#</th>
				                    <th>Ip</th>
				                    <th>Port</th>
				                    <th>timeout</th>
				                    <th>database</th>
				                    <th></th>
				                </tr>
				            </thead>
				            <tbody>
				            	<#list slaves as slave>
				            	<tr>
				            		<td>${slave_index + 1}</td>
									<td>${slave.host}</td>
									<td>${slave.port}</td>
									<td>${slave.timeout}</td>
									<td>${slave.database}</td>
									<td><a href="#">detail</a></td>
				            	</tr>
				            	</#list>
				            </tbody>
						</table>
					</div>
				</div>
			</div>
		</div>
	</body>
</html>
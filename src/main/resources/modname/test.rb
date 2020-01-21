require './draminate'

count = ARGV[0]? ARGV[0].to_i : 10
search = Regexp.new(ARGV[1]) if ARGV[1]

if search
  puts "Printing #{count} dramas matching #{search}"
  found = 0
  (count * 1000).times do
	seed = Random.new_seed
	Random.srand(seed)
	drama = draminate
	if drama =~ search
	  puts "[#{seed.to_s(36)}] #{drama}"
	  found += 1
	  exit if found == count
	end
  end
  puts "Failed to find #{count} dramas matching #{search} after searching through #{count*1000} seeds."
else
  puts "Printing #{count} dramas."
  count.times do
	seed = Random.new_seed
	Random.srand(seed)
	drama = draminate
	puts "[#{seed.to_s(36)}] #{drama}"
  end
end
